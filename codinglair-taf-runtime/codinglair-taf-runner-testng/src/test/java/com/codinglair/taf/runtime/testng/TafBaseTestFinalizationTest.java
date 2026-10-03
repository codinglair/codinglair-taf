package com.codinglair.taf.runtime.testng;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.runtime.core.TestSession;
import com.codinglair.taf.runtime.core.history.ExecutionAttemptSummary;
import com.codinglair.taf.runtime.core.history.ExecutionHistoryRepository;
import com.codinglair.taf.runtime.core.history.HistoryResult;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflightContributor;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflightException;
import com.codinglair.taf.runtime.core.preflight.PreflightDiagnostic;
import com.codinglair.taf.runtime.core.reporting.CurrentReportingContext;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestReporter;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestStep;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.testng.IConfigurationListener;
import org.testng.ITestResult;
import org.testng.TestNG;
import org.testng.annotations.AfterMethod;

@DisplayName("TestNG invocation finalization failures")
class TafBaseTestFinalizationTest {
  private static Phase phase;
  private static FailureSource source;
  private static Consumer consumer;
  private static int bodyCalls;
  private static int cleanupCalls;
  private static int endCalls;

  @BeforeEach
  void reset() {
    consumer = null;
    bodyCalls = 0;
    cleanupCalls = 0;
    endCalls = 0;
  }

  @Nested
  @DisplayName("Cleanup after secondary failures")
  class SecondaryFailures {
    @ParameterizedTest(name = "{0} with {1} failure")
    @CsvSource({"SETUP,HISTORY", "SETUP,REPORTING", "TEARDOWN,HISTORY", "TEARDOWN,REPORTING"})
    @DisplayName("Always unbinds invocation state and completes reporting after finalization fails")
    void clearsState(Phase selectedPhase, FailureSource selectedSource) {
      phase = selectedPhase;
      source = selectedSource;
      var failures = new ArrayList<Throwable>();
      var testng = new TestNG(false);
      testng.setUseDefaultListeners(false);
      testng.setTestClasses(new Class<?>[] {Consumer.class});
      testng.addListener(
          new IConfigurationListener() {
            @Override
            public void onConfigurationFailure(ITestResult result) {
              failures.add(result.getThrowable());
            }
          });
      testng.run();

      assertThat(testng.hasFailure()).isTrue();
      assertThat(failures).hasSize(1);
      String secondaryMessage =
          source == FailureSource.HISTORY ? "history failure" : "reporting failure";
      if (phase == Phase.SETUP) {
        assertThat(failures.getFirst())
            .isInstanceOf(ConsumerPreflightException.class)
            .hasMessageContaining("fixture.preflight");
        assertThat(failures.getFirst().getSuppressed())
            .anySatisfy(failure -> assertThat(failure).hasMessageContaining(secondaryMessage));
      } else {
        assertThat(failures.getFirst()).hasMessageContaining(secondaryMessage);
      }
      assertThat(bodyCalls).isEqualTo(phase == Phase.SETUP ? 0 : 1);
      assertThat(cleanupCalls).isEqualTo(1);
      assertThat(consumer).isNotNull();
      consumer.assertStateCleared();
      assertThat(endCalls).isEqualTo(1);
    }
  }

  enum Phase {
    SETUP,
    TEARDOWN
  }

  enum FailureSource {
    HISTORY,
    REPORTING
  }

  @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
  @ContextConfiguration(classes = FailingConfiguration.class)
  public static class Consumer extends TafBaseTest {
    @Autowired private CurrentReportingContext current;
    private ITestResult observed;

    public Consumer() {
      consumer = this;
    }

    @org.testng.annotations.Test
    public void interaction() {
      bodyCalls++;
    }

    @AfterMethod(alwaysRun = true)
    public void observe(ITestResult result) {
      observed = result;
    }

    void assertStateCleared() {
      assertThat(current.current()).isEmpty();
      assertThatThrownBy(this::testCaseId).hasMessageContaining("No TestNG invocation metadata");
      assertThatThrownBy(() -> step("after invocation", () -> {}))
          .hasMessageContaining("No TestNG reporting scope");
      assertThat(SessionFactory.hasSession(observed)).isFalse();
      assertThatCode(() -> closeTafSession(observed)).doesNotThrowAnyException();
      assertThat(cleanupCalls).isEqualTo(1);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class FailingConfiguration {
    @Bean
    TestSessionFactory testSessionFactory() {
      return () -> {
        var session = TestSession.create();
        session.addCleanupListener(_ -> cleanupCalls++);
        return session;
      };
    }

    @Bean
    ConsumerPreflightContributor consumerPreflightContributor() {
      return () ->
          phase == Phase.SETUP
              ? List.of(
                  new PreflightDiagnostic(
                      "fixture.preflight", "Consumer prerequisite missing", "Supply prerequisite"))
              : List.of();
    }

    @Bean
    ExecutionHistoryRepository executionHistoryRepository() {
      return new ExecutionHistoryRepository() {
        @Override
        public HistoryResult record(ExecutionAttemptSummary summary) {
          return history();
        }

        @Override
        public HistoryResult findByTest(String projectId, String testId, int limit, Duration age) {
          return history();
        }

        @Override
        public HistoryResult findBySignature(
            String projectId, String signature, int limit, Duration age) {
          return history();
        }

        private HistoryResult history() {
          if (source == FailureSource.HISTORY) throw new IllegalStateException("history failure");
          return new HistoryResult(HistoryResult.Status.DISABLED, List.of(), "");
        }
      };
    }

    @Bean
    TestReporter testReporter() {
      return new TestReporter() {
        @Override
        public void beginTest(TafTest test) {}

        @Override
        public void endTest(TafTest test) {
          endCalls++;
          if (source == FailureSource.REPORTING) throw new AssertionError("reporting failure");
        }

        @Override
        public void reportStep(TestStep step) {}

        @Override
        public void reportArtifact(TestArtifact artifact) {}

        @Override
        public String getName() {
          return "finalization-fixture";
        }
      };
    }
  }
}

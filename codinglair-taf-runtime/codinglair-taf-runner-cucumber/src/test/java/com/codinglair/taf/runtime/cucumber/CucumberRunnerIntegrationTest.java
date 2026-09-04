package com.codinglair.taf.runtime.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.cucumber.glue.AccountSteps;
import io.cucumber.core.feature.FeatureWithLines;
import io.cucumber.core.options.RuntimeOptions;
import io.cucumber.core.options.RuntimeOptionsBuilder;
import io.cucumber.core.runtime.Runtime;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CucumberRunnerIntegrationTest {
  private static final URI GLUE = URI.create("classpath:com/codinglair/taf/runtime/cucumber");

  @BeforeEach
  void resetCleanupEvidence() {
    AccountSteps.CLEANUPS.set(0);
    AccountSteps.TEST_CASE_IDS.clear();
  }

  @Test
  void curatedFeatureMapsStepsTraceabilityAndSanitizedEvidenceToBusinessReport() {
    CapturingBusinessListener listener = new CapturingBusinessListener();
    CucumberBusinessReportPlugin plugin = new CucumberBusinessReportPlugin(List.of(listener));

    byte exitStatus = run("classpath:features/account_balance.feature", plugin, 1);

    assertThat(exitStatus).isZero();
    assertThat(AccountSteps.CLEANUPS).hasValue(1);
    assertThat(AccountSteps.TEST_CASE_IDS).containsExactly("ACCT-TC-101");
    assertThat(plugin.results())
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.audience()).isEqualTo("BUSINESS");
              assertThat(result.traceabilityTags()).contains("@requirement-ACCT-101");
              assertThat(result.steps())
                  .extracting(BusinessStepResult::text)
                  .containsExactly(
                      "an account with a zero balance",
                      "the customer deposits 25 dollars",
                      "the account balance is 25 dollars");
              assertThat(result.artifacts())
                  .singleElement()
                  .satisfies(
                      artifact -> {
                        assertThat(artifact.stepName())
                            .isEqualTo("the customer deposits 25 dollars");
                        assertThat(artifact.content())
                            .doesNotContain("business-secret")
                            .contains("****");
                      });
            });
    assertThat(listener.results).containsExactlyElementsOf(plugin.results());
  }

  @Test
  void failureStillRunsCleanupAndBusinessReportOmitsStackTrace() {
    CucumberBusinessReportPlugin plugin = new CucumberBusinessReportPlugin(List.of());

    byte exitStatus = run("classpath:features/failed_cleanup.feature", plugin, 1);

    assertThat(exitStatus).isEqualTo((byte) 1);
    assertThat(AccountSteps.CLEANUPS).hasValue(1);
    assertThat(plugin.results())
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.status()).isEqualTo("FAILED");
              assertThat(result.failureAnalysis()).isNotNull();
              assertThat(result.failureAnalysis().classification().type())
                  .isEqualTo(com.codinglair.taf.core.Error.ErrorType.INCONCLUSIVE);
              assertThat(result.failureAnalysis().signature()).isNotNull();
              assertThat(result.steps())
                  .extracting(BusinessStepResult::status)
                  .containsExactly("PASSED", "FAILED", "SKIPPED");
              assertThat(result.toString())
                  .doesNotContain("AccountSteps.java", "declined by the business service");
            });
  }

  @Test
  void cleanupFailureFailsScenarioAndRemainsVisibleWithoutTechnicalDetails() {
    CucumberBusinessReportPlugin plugin = new CucumberBusinessReportPlugin(List.of());

    byte exitStatus = run("classpath:features/cleanup_failure.feature", plugin, 1);

    assertThat(exitStatus).isEqualTo((byte) 1);
    assertThat(AccountSteps.CLEANUPS).hasValue(1);
    assertThat(plugin.results())
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.status()).isEqualTo("FAILED");
              assertThat(result.steps())
                  .extracting(BusinessStepResult::status)
                  .containsExactly("PASSED", "PASSED");
              assertThat(result.toString())
                  .doesNotContain("cleanup failed", "IllegalStateException");
            });
  }

  @Test
  void parallelScenariosRetainIsolatedStateAndCleanup() {
    CucumberBusinessReportPlugin plugin = new CucumberBusinessReportPlugin(List.of());

    byte exitStatus = run("classpath:features/parallel_balances.feature", plugin, 4);

    assertThat(exitStatus).isZero();
    assertThat(AccountSteps.CLEANUPS).hasValue(8);
    assertThat(plugin.results()).hasSize(8);
    assertThat(plugin.results())
        .extracting(CucumberBusinessResult::scenarioId)
        .doesNotHaveDuplicates();
  }

  @Test
  void undefinedStepSkipsRemainingScenarioAndStillCleansSession() {
    CucumberBusinessReportPlugin plugin = new CucumberBusinessReportPlugin(List.of());

    byte exitStatus = run("classpath:features/skipped_scenario.feature", plugin, 1);

    assertThat(exitStatus).isEqualTo((byte) 1);
    assertThat(AccountSteps.CLEANUPS).hasValue(1);
    assertThat(plugin.results())
        .singleElement()
        .extracting(CucumberBusinessResult::status)
        .isEqualTo("UNDEFINED");
  }

  @Test
  void consumerBeforeHookFailureStillCleansFrameworkOwnedSession() {
    CucumberBusinessReportPlugin plugin = new CucumberBusinessReportPlugin(List.of());

    byte exitStatus = run("classpath:features/hook_failure.feature", plugin, 1);

    assertThat(exitStatus).isEqualTo((byte) 1);
    assertThat(AccountSteps.CLEANUPS).hasValue(1);
    assertThat(plugin.results())
        .singleElement()
        .satisfies(result -> {
          assertThat(result.status()).isEqualTo("FAILED");
          assertThat(result.failureAnalysis()).isNotNull();
          assertThat(result.failureAnalysis().signature()).isNotNull();
          assertThat(result.failureAnalysis().classification().type())
              .isEqualTo(com.codinglair.taf.core.Error.ErrorType.INCONCLUSIVE);
        });
  }

  private static byte run(String feature, CucumberBusinessReportPlugin plugin, int threads) {
    RuntimeOptions options =
        new RuntimeOptionsBuilder()
            .addFeature(FeatureWithLines.parse(feature))
            .addGlue(GLUE)
            .setThreads(threads)
            .setPublish(false)
            .setNoSummary()
            .build();
    Runtime runtime =
        Runtime.builder()
            .withRuntimeOptions(options)
            .withClassLoader(() -> Thread.currentThread().getContextClassLoader())
            .withAdditionalPlugins(plugin)
            .build();
    runtime.run();
    return runtime.exitStatus();
  }

  private static final class CapturingBusinessListener implements CucumberBusinessResultListener {
    private final List<CucumberBusinessResult> results =
        new java.util.concurrent.CopyOnWriteArrayList<>();

    @Override
    public void onResult(CucumberBusinessResult result) {
      results.add(result);
    }
  }
}

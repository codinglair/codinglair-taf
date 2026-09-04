package com.codinglair.taf.runtime.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.TestSession;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionLifecycle;
import com.codinglair.taf.runtime.core.reporting.CurrentReportingContext;
import com.codinglair.taf.runtime.core.reporting.ReportingActionInterceptor;
import com.codinglair.taf.runtime.core.reporting.ReportingBeanPostProcessor;
import com.codinglair.taf.runtime.core.reporting.abstraction.ReportEvent;
import com.codinglair.taf.runtime.core.reporting.abstraction.ReportLevel;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestReporter;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestStep;
import com.codinglair.taf.runtime.core.reporting.annotation.BddStep;
import io.cucumber.java.Scenario;
import io.cucumber.java.Status;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class CucumberAutomaticReportingTest {
  @Test
  void hooksOwnScenarioScopeAndBddActionWithoutTestNgDuplicate() throws Exception {
    var current = new CurrentReportingContext();
    var reporter = new RecordingReporter();
    var session =
        new CucumberScenarioSession(
            new TestSessionLifecycle(TestSession::create), current, List.of(reporter));
    var hooks = new TafCucumberHooks(session);
    var glue =
        (BusinessGlue)
            new ReportingBeanPostProcessor(new ReportingActionInterceptor(current))
                .postProcessAfterInitialization(new BusinessGlue(), "businessGlue");

    hooks.openSession(scenario("scenario-1", "Purchase", Status.PASSED));
    glue.purchase();
    hooks.closeSession();

    List<ReportEvent> finished =
        reporter.events.stream()
            .filter(event -> event.phase() == ReportEvent.Phase.FINISHED)
            .toList();
    assertThat(finished)
        .extracting(ReportEvent::level)
        .containsExactly(ReportLevel.BDD_STEP, ReportLevel.SCENARIO);
    assertThat(reporter.beginCount).hasValue(1);
    assertThat(reporter.endCount).hasValue(1);
  }

  @Test
  void parallelHookInvocationsRemainIsolated() throws Exception {
    var current = new CurrentReportingContext();
    var reporter = new RecordingReporter();
    var lifecycle = new TestSessionLifecycle(TestSession::create);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var tasks =
          java.util.stream.IntStream.range(0, 8)
              .<java.util.concurrent.Callable<Void>>mapToObj(
                  index ->
                      () -> {
                        var session =
                            new CucumberScenarioSession(lifecycle, current, List.of(reporter));
                        var hooks = new TafCucumberHooks(session);
                        hooks.openSession(
                            scenario("scenario-" + index, "Scenario " + index, Status.PASSED));
                        hooks.closeSession();
                        return null;
                      })
              .toList();
      for (var result : executor.invokeAll(tasks)) result.get();
    }
    assertThat(reporter.beginCount).hasValue(8);
    assertThat(reporter.endCount).hasValue(8);
    assertThat(
            reporter.events.stream()
                .filter(event -> event.phase() == ReportEvent.Phase.FINISHED)
                .map(ReportEvent::correlationId))
        .doesNotHaveDuplicates();
  }

  private static Scenario scenario(String id, String name, Status status) throws Exception {
    io.cucumber.core.backend.TestCaseState state =
        (io.cucumber.core.backend.TestCaseState)
            Proxy.newProxyInstance(
                Scenario.class.getClassLoader(),
                new Class<?>[] {io.cucumber.core.backend.TestCaseState.class},
                (proxy, method, arguments) ->
                    switch (method.getName()) {
                      case "getId" -> id;
                      case "getName" -> name;
                      case "getStatus" -> io.cucumber.core.backend.Status.valueOf(status.name());
                      case "getSourceTagNames" -> Set.of();
                      case "isFailed" -> status == Status.FAILED;
                      case "toString" -> name;
                      default -> defaultValue(method.getReturnType());
                    });
    var constructor =
        Scenario.class.getDeclaredConstructor(io.cucumber.core.backend.TestCaseState.class);
    constructor.setAccessible(true);
    return constructor.newInstance(state);
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) return null;
    if (type == boolean.class) return false;
    if (type == char.class) return '\0';
    return 0;
  }

  static class BusinessGlue {
    @BddStep("Purchase product")
    public void purchase() {}
  }

  private static final class RecordingReporter implements TestReporter {
    private final List<ReportEvent> events = new CopyOnWriteArrayList<>();
    private final java.util.concurrent.atomic.AtomicInteger beginCount =
        new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger endCount =
        new java.util.concurrent.atomic.AtomicInteger();

    @Override
    public void beginTest(TafTest test) {
      beginCount.incrementAndGet();
    }

    @Override
    public void endTest(TafTest test) {
      endCount.incrementAndGet();
    }

    @Override
    public void reportEvent(ReportEvent event) {
      events.add(event);
    }

    @Override
    public void reportStep(TestStep step) {}

    @Override
    public void reportArtifact(TestArtifact artifact) {}

    @Override
    public String getName() {
      return "recording";
    }
  }
}

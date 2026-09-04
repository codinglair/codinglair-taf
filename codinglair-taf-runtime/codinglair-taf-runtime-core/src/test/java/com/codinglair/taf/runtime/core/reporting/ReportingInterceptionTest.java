package com.codinglair.taf.runtime.core.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.runtime.core.TestSession;
import com.codinglair.taf.runtime.core.controller.ArtifactReason;
import com.codinglair.taf.runtime.core.controller.ControllerContext;
import com.codinglair.taf.runtime.core.controller.ControllerIdentity;
import com.codinglair.taf.runtime.core.controller.ControllerState;
import com.codinglair.taf.runtime.core.controller.HealthResult;
import com.codinglair.taf.runtime.core.controller.TestController;
import com.codinglair.taf.runtime.core.reporting.abstraction.ReportEvent;
import com.codinglair.taf.runtime.core.reporting.abstraction.ReportLevel;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import com.codinglair.taf.runtime.core.reporting.annotation.ControllerAction;
import com.codinglair.taf.runtime.core.reporting.annotation.PageAction;
import com.codinglair.taf.runtime.core.reporting.annotation.Validation;
import com.codinglair.taf.runtime.core.reporting.annotation.Workflow;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ReportingInterceptionTest {
  private final CurrentReportingContext current = new CurrentReportingContext();
  private final ReportingActionInterceptor interceptor = new ReportingActionInterceptor(current);

  @Test
  void springManagedWorkflowRetainsPageControllerAndValidationHierarchy() {
    var hierarchy = new HierarchicalReport("spring-invocation", new RedactionPipeline());
    var context = new ReportingContext(hierarchy, List.of());
    current.bind(context);
    try {
      var target = new CheckoutPage();
      var page =
          (CheckoutPage)
              new ReportingBeanPostProcessor(interceptor)
                  .postProcessAfterInitialization(target, "checkoutPage");
      var workflowTarget = new CheckoutWorkflow(page);
      var workflow =
          (CheckoutWorkflow)
              new ReportingBeanPostProcessor(interceptor)
                  .postProcessAfterInitialization(workflowTarget, "checkoutWorkflow");
      TestSession session = TestSession.create();
      session.getControllerRegistry().enableReporting(interceptor);
      session
          .getControllerRegistry()
          .register(DemoController.class, "web", new DemoControllerImpl());
      target.controller = session.getController(DemoController.class, "web");

      workflow.checkout("token=literal-secret", "complete");

      List<ReportEvent> finished = finished(context);
      assertThat(finished)
          .extracting(ReportEvent::level)
          .containsExactly(
              ReportLevel.CONTROLLER_OPERATION,
              ReportLevel.PAGE,
              ReportLevel.VALIDATION,
              ReportLevel.WORKFLOW);
      assertThat(finished)
          .allSatisfy(event -> assertThat(event.toString()).doesNotContain("literal-secret"));
      ReportEvent pageEvent = event(finished, ReportLevel.PAGE);
      ReportEvent controllerEvent = event(finished, ReportLevel.CONTROLLER_OPERATION);
      assertThat(controllerEvent.parentId()).isEqualTo(pageEvent.id());
      assertThat(event(finished, ReportLevel.VALIDATION).context())
          .containsEntry("expected", "****")
          .containsEntry("actual", "complete");
      session.close();
    } finally {
      current.unbind();
    }
  }

  @Test
  void explicitPageActionHasNoArtificialWorkflowAndFailureFinalizesOnce() {
    var hierarchy = new HierarchicalReport("explicit", new RedactionPipeline());
    var context = new ReportingContext(hierarchy, List.of());
    current.bind(context);
    try {
      var page =
          (CheckoutPage)
              new ReportingBeanPostProcessor(interceptor)
                  .postProcessAfterInitialization(new CheckoutPage(), "checkoutPage");

      assertThatThrownBy(page::fails).isInstanceOf(IllegalStateException.class);

      List<ReportEvent> finished = finished(context);
      assertThat(finished).hasSize(1);
      assertThat(finished.getFirst().level()).isEqualTo(ReportLevel.PAGE);
      assertThat(finished.getFirst().status()).isEqualTo("FAILED");
    } finally {
      current.unbind();
    }
  }

  @Test
  void absentAdapterAndAbsentInvocationLeaveConsumerObjectUsable() {
    var target = new CheckoutPage();
    var page =
        (CheckoutPage)
            new ReportingBeanPostProcessor(interceptor)
                .postProcessAfterInitialization(target, "checkoutPage");
    page.open();
    assertThat(target.calls).isEqualTo(1);
  }

  @Test
  void parallelInvocationsRetainIndependentParentsAndSanitizedValues() throws Exception {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var results =
          executor.invokeAll(
              java.util.stream.IntStream.range(0, 12)
                  .<java.util.concurrent.Callable<List<ReportEvent>>>mapToObj(
                      index ->
                          () -> {
                            var localCurrent = new CurrentReportingContext();
                            var localInterceptor = new ReportingActionInterceptor(localCurrent);
                            var hierarchy =
                                new HierarchicalReport(
                                    "parallel-" + index, new RedactionPipeline());
                            var context = new ReportingContext(hierarchy, List.of());
                            localCurrent.bind(context);
                            try {
                              var page =
                                  (CheckoutPage)
                                      new ReportingBeanPostProcessor(localInterceptor)
                                          .postProcessAfterInitialization(
                                              new CheckoutPage(), "page-" + index);
                              page.validate("token=secret-" + index, "actual-" + index);
                              return context.events();
                            } finally {
                              localCurrent.unbind();
                            }
                          })
                  .toList());
      assertThat(results)
          .allSatisfy(
              result -> {
                List<ReportEvent> events = result.get();
                assertThat(events).hasSize(2);
                assertThat(events)
                    .allSatisfy(event -> assertThat(event.toString()).doesNotContain("secret-"));
              });
    }
  }

  private static List<ReportEvent> finished(ReportingContext context) {
    return context.events().stream()
        .filter(event -> event.phase() == ReportEvent.Phase.FINISHED)
        .toList();
  }

  private static ReportEvent event(List<ReportEvent> events, ReportLevel level) {
    return events.stream().filter(event -> event.level() == level).findFirst().orElseThrow();
  }

  static class CheckoutPage {
    private DemoController controller;
    private int calls;

    @PageAction("Open checkout")
    public void open() {
      calls++;
      if (controller != null) controller.navigate();
    }

    @Validation("Checkout state")
    public void validate(String expected, String actual) {}

    @PageAction("Fail checkout")
    public void fails() {
      throw new IllegalStateException("token=literal-secret");
    }
  }

  static class CheckoutWorkflow {
    private final CheckoutPage page;

    CheckoutWorkflow(CheckoutPage page) {
      this.page = page;
    }

    @Workflow("Checkout workflow")
    public void checkout(String expected, String actual) {
      page.open();
      page.validate(expected, actual);
    }
  }

  interface DemoController extends TestController {
    void navigate();
  }

  static final class DemoControllerImpl implements DemoController {
    private ControllerState state = ControllerState.NEW;

    @Override
    @ControllerAction("Navigate")
    public void navigate() {}

    @Override
    public ControllerIdentity identity() {
      return new ControllerIdentity(DemoController.class, "web");
    }

    @Override
    public ControllerState state() {
      return state;
    }

    @Override
    public void initialize(ControllerContext context) {
      state = ControllerState.READY;
    }

    @Override
    public HealthResult health() {
      return new HealthResult(HealthResult.Status.HEALTHY, "ready", java.util.Map.of());
    }

    @Override
    public Stream<TestArtifact> collectArtifacts(ArtifactReason reason) {
      return Stream.empty();
    }

    @Override
    public void close() {
      state = ControllerState.CLOSED;
    }
  }
}

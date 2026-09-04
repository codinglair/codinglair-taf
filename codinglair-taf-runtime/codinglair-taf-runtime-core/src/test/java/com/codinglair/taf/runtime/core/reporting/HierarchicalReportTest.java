package com.codinglair.taf.runtime.core.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.reporting.abstraction.ReportEvent;
import com.codinglair.taf.runtime.core.reporting.abstraction.ReportLevel;
import com.codinglair.taf.runtime.core.reporting.abstraction.ValidationContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HierarchicalReportTest {
  @Test
  void recordsGoldenHierarchyExactlyOnceAndSanitizesFailureContext() {
    var report = new HierarchicalReport("scenario-1", new RedactionPipeline());
    try (var workflow = report.open("workflow", ReportLevel.WORKFLOW, "purchase", null)) {
      try (var action = report.open("action", ReportLevel.CONSUMER_ACTION, "submit order", null)) {
        try (var controller =
            report.open("controller", ReportLevel.CONTROLLER_OPERATION, "post checkout", null)) {
          report.validation(
              "validation",
              "status is accepted",
              "FAILED",
              new ValidationContext(
                  "202", "password=hunter2", Map.of("token=metadata", "secret=value")));
        }
      }
    }
    try (var duplicate =
        report.open("controller", ReportLevel.CONTROLLER_OPERATION, "post checkout", null)) {
      duplicate.fail("duplicate observer");
    }

    assertThat(report.events())
        .extracting(ReportEvent::id)
        .containsExactly(
            "workflow",
            "action",
            "controller",
            "validation",
            "validation",
            "controller",
            "action",
            "workflow");
    assertThat(report.events()).filteredOn(event -> event.id().equals("controller")).hasSize(2);
    assertThat(report.events()).allMatch(event -> event.correlationId().equals("scenario-1"));
    assertThat(report.events().toString()).doesNotContain("hunter2");
    assertThat(report.events().toString()).doesNotContain("metadata", "value");
    assertThat(report.events())
        .filteredOn(
            event -> event.id().equals("validation") && event.phase() == ReportEvent.Phase.FINISHED)
        .singleElement()
        .satisfies(event -> assertThat(event.context().get("actual")).isEqualTo("****"));
  }

  @Test
  void repeatedCloseIsIdempotent() {
    var report = new HierarchicalReport("test-1", new RedactionPipeline());
    var scope = report.open("step", ReportLevel.PAGE, "open page", null);
    scope.fail("boom");
    scope.close();
    assertThat(report.events()).hasSize(2);
  }

  @Test
  void everyConsumerLevelAppearsOnceAsACompletedLogicalStep() {
    var report = new HierarchicalReport("consumer-test", new RedactionPipeline());
    List<ReportLevel> levels =
        List.of(
            ReportLevel.TEST,
            ReportLevel.SCENARIO,
            ReportLevel.WORKFLOW,
            ReportLevel.PAGE,
            ReportLevel.COMPONENT,
            ReportLevel.API,
            ReportLevel.SCREEN,
            ReportLevel.BDD_STEP,
            ReportLevel.CONSUMER_ACTION,
            ReportLevel.CONTROLLER_OPERATION);
    var scopes = new ArrayList<HierarchicalReport.Scope>();
    for (ReportLevel level : levels)
      scopes.add(report.open(level.name(), level, level.name(), null));
    for (int index = scopes.size() - 1; index >= 0; index--) scopes.get(index).close();
    report.validation(
        "VALIDATION", "VALIDATION", "PASSED", new ValidationContext("ok", "ok", Map.of()));

    assertThat(report.events())
        .filteredOn(event -> event.phase() == ReportEvent.Phase.FINISHED)
        .extracting(ReportEvent::level)
        .containsExactlyInAnyOrder(
            ReportLevel.TEST,
            ReportLevel.SCENARIO,
            ReportLevel.WORKFLOW,
            ReportLevel.PAGE,
            ReportLevel.COMPONENT,
            ReportLevel.API,
            ReportLevel.SCREEN,
            ReportLevel.BDD_STEP,
            ReportLevel.CONSUMER_ACTION,
            ReportLevel.CONTROLLER_OPERATION,
            ReportLevel.VALIDATION);
  }
}

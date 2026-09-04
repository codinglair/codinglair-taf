package com.codinglair.taf.mcp.prompts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.codinglair.taf.mcp.resources.EvidenceAccessScope;
import com.codinglair.taf.mcp.resources.ResourceAccessDeniedException;
import com.codinglair.taf.mcp.resources.ResourceRequestContext;
import com.codinglair.taf.mcp.security.ApprovalService;
import com.codinglair.taf.mcp.security.AuthorizationPolicyEngine;
import com.codinglair.taf.mcp.security.CallerIdentity;
import com.codinglair.taf.mcp.security.IdentityKind;
import com.codinglair.taf.mcp.security.InMemoryAuditLog;
import com.codinglair.taf.mcp.security.McpEnforcementService;
import com.codinglair.taf.mcp.security.PolicyRule;
import com.codinglair.taf.mcp.security.ResponseRedactor;
import com.codinglair.taf.mcp.security.Transport;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MCP prompt, report, and diagnostic service")
class McpPromptReportServiceTest {
  private static final CallerIdentity IDENTITY =
      new CallerIdentity("user", Set.of("qa"), "agent", IdentityKind.AGENT);
  private static final ResourceRequestContext CALLER =
      new ResourceRequestContext(IDENTITY, "project", "local");

  @Nested
  @DisplayName("Prompt catalog")
  class PromptCatalogTests {
    @Test
    @DisplayName("publishes deterministic versioned secret-free QA workflows")
    void publishesVersionedPrompts() {
      var fixture = fixture(Set.of(McpPromptReportService.REPORT_READ), List.of());

      var prompts = fixture.service.prompts(CALLER, "correlation", Transport.STDIO);

      assertThat(prompts)
          .extracting(PromptDefinition::name)
          .containsExactly(
              "taf.qa.environment-triage", "taf.qa.execution-summary", "taf.qa.failure-analysis");
      assertThat(prompts).allMatch(prompt -> prompt.schemaVersion().equals("1.0"));
      assertThat(fixture.audit.events())
          .extracting(event -> event.type())
          .contains("tool.invoked", "tool.completed");
    }

    @Test
    @DisplayName("rejects prompt text containing secret-like assignments")
    void rejectsSecretMaterial() {
      assertThrows(
          IllegalArgumentException.class,
          () -> new PromptMessage(PromptMessage.Role.USER, "password=do-not-store"));
    }
  }

  @Nested
  @DisplayName("Controlled resources")
  class ControlledResources {
    @Test
    @DisplayName("paginates large UTF-8 reports with resource-bound cursors")
    void paginatesLargeReports() {
      var content = "é".repeat(McpPromptReportService.MAX_CHUNK_BYTES);
      var resource = report("report", "text/plain", content);
      var fixture = fixture(Set.of(McpPromptReportService.REPORT_READ), List.of(resource));

      var first = fixture.service.report(CALLER, "first", Transport.STDIO, "job-1", null);
      var second =
          fixture.service.report(CALLER, "second", Transport.STDIO, "job-1", first.nextCursor());

      assertThat(first.content().getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
          .isLessThanOrEqualTo(McpPromptReportService.MAX_CHUNK_BYTES);
      assertThat(first.nextCursor()).isNotNull();
      assertThat(first.content() + second.content()).isEqualTo(content);
      assertThat(second.nextCursor()).isNull();
    }

    @Test
    @DisplayName("returns only a controlled reference for binary evidence")
    void referencesBinaryEvidence() {
      var resource = evidence("screen", "image/png", "encoded-binary-placeholder");
      var fixture = fixture(Set.of(McpPromptReportService.ARTIFACT_READ), List.of(resource));

      var result =
          fixture.service.evidence(CALLER, "binary", Transport.STDIO, "job-1", "screen", null);

      assertThat(result.referenceOnly()).isTrue();
      assertThat(result.content()).isEmpty();
      assertThat(result.uri()).isEqualTo("taf://evidence/job-1/screen");
    }

    @Test
    @DisplayName("denies access before consulting the report repository")
    void deniesBeforeLookup() {
      var calls = new AtomicInteger();
      var fixture =
          fixture(
              Set.of(),
              _ -> {
                calls.incrementAndGet();
                return List.of();
              });

      assertThrows(
          ResourceAccessDeniedException.class,
          () -> fixture.service.report(CALLER, "denied", Transport.STDIO, "job-1", null));
      assertThat(calls).hasValue(0);
      assertThat(fixture.audit.events())
          .extracting(event -> event.type())
          .contains("authorization.decided");
    }

    @Test
    @DisplayName("does not expose another owner or scope")
    void enforcesStoredScope() {
      var hidden =
          new StoredResource(
              new EvidenceAccessScope("other", "project", "local"),
              "job-1",
              "report",
              "taf://report/job-1",
              "application/json",
              "{}",
              true);
      var fixture = fixture(Set.of(McpPromptReportService.REPORT_READ), List.of(hidden));

      assertThrows(
          ResourceAccessDeniedException.class,
          () -> fixture.service.report(CALLER, "hidden", Transport.STDIO, "job-1", null));
    }

    @Test
    @DisplayName("redacts canary values before report content leaves the boundary")
    void redactsReportContent() {
      var fixture =
          fixture(
              Set.of(McpPromptReportService.REPORT_READ),
              List.of(report("report", "text/plain", "failure canary-secret detail")));

      var result = fixture.service.report(CALLER, "redact", Transport.STDIO, "job-1", null);

      assertThat(result.content())
          .doesNotContain("canary-secret")
          .contains(ResponseRedactor.REDACTED);
    }
  }

  @Nested
  @DisplayName("Controlled diagnostics")
  class ControlledDiagnostics {
    @Test
    @DisplayName("requires a separately granted diagnostic permission")
    void requiresSeparatePermission() {
      var fixture =
          fixture(
              Set.of(McpPromptReportService.REPORT_READ),
              List.of(report("report", "application/json", "{}")));

      assertThrows(
          ResourceAccessDeniedException.class,
          () ->
              fixture.service.diagnose(
                  CALLER,
                  "diagnose",
                  Transport.STREAMABLE_HTTP,
                  "job-1",
                  DiagnosticKind.REPORT_METADATA));
    }

    @Test
    @DisplayName("returns only bounded metadata and controlled references")
    void returnsBoundedReadOnlySummary() {
      var resources = new ArrayList<StoredResource>();
      resources.add(report("report", "application/json", "{}"));
      for (var index = 0; index < 140; index++) {
        resources.add(evidence("item-" + index, "text/plain", "safe"));
      }
      var fixture = fixture(Set.of(McpPromptReportService.DIAGNOSTIC_READ), resources);

      var result =
          fixture.service.diagnose(
              CALLER,
              "diagnose",
              Transport.STREAMABLE_HTTP,
              "job-1",
              DiagnosticKind.EVIDENCE_INVENTORY);

      assertThat(result.resourceReferences())
          .hasSize(100)
          .allMatch(uri -> uri.startsWith("taf://"));
      assertThat(result.summary()).containsEntry("truncated", "true");
      assertThat(fixture.audit.events())
          .extracting(event -> event.type())
          .contains("tool.invoked", "tool.completed");
    }

    @Test
    @DisplayName("supports concurrent reads without shared cursor or diagnostic state")
    void supportsConcurrentReads() {
      var fixture =
          fixture(
              Set.of(McpPromptReportService.DIAGNOSTIC_READ),
              List.of(report("report", "application/json", "{}")));
      assertTimeoutPreemptively(
          Duration.ofSeconds(5),
          () -> {
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
              var tasks =
                  java.util.stream.IntStream.range(0, 32)
                      .<java.util.concurrent.Callable<DiagnosticResult>>mapToObj(
                          index ->
                              () ->
                                  fixture.service.diagnose(
                                      CALLER,
                                      "diagnose-" + index,
                                      Transport.STDIO,
                                      "job-1",
                                      DiagnosticKind.REPORT_METADATA))
                      .toList();
              assertThat(executor.invokeAll(tasks))
                  .allSatisfy(future -> assertThat(future.get().jobId()).isEqualTo("job-1"));
            }
          });
    }
  }

  private static StoredResource report(String id, String mediaType, String content) {
    return new StoredResource(
        new EvidenceAccessScope("user", "project", "local"),
        "job-1",
        id,
        "taf://report/job-1",
        mediaType,
        content,
        true);
  }

  private static StoredResource evidence(String id, String mediaType, String content) {
    return new StoredResource(
        new EvidenceAccessScope("user", "project", "local"),
        "job-1",
        id,
        "taf://evidence/job-1/" + id,
        mediaType,
        content,
        true);
  }

  private static Fixture fixture(Set<String> permissions, List<StoredResource> resources) {
    return fixture(permissions, _ -> List.copyOf(resources));
  }

  private static Fixture fixture(Set<String> permissions, ReportRepository repository) {
    var clock = Clock.systemUTC();
    var redactor = new ResponseRedactor(Set.of("canary-secret"));
    var audit = new InMemoryAuditLog(clock, redactor);
    var policy =
        new AuthorizationPolicyEngine(
            permissions.stream()
                .map(
                    permission ->
                        new PolicyRule(
                            "allow-" + permission,
                            PolicyRule.Effect.ALLOW,
                            Set.of("user"),
                            Set.of("qa"),
                            Set.of("project"),
                            Set.of("local"),
                            Set.of("*"),
                            Set.of("agent"),
                            Set.of(permission),
                            false))
                .toList());
    var enforcement =
        new McpEnforcementService(policy, new ApprovalService(clock), audit, redactor);
    return new Fixture(
        new McpPromptReportService(PromptCatalog.standard(), repository, enforcement), audit);
  }

  private record Fixture(McpPromptReportService service, InMemoryAuditLog audit) {}
}

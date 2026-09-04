package com.codinglair.taf.mcp.stdio;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.mcp.jobs.JobRepository;
import com.codinglair.taf.mcp.jobs.JobService;
import com.codinglair.taf.mcp.jobs.LocalJobRepository;
import com.codinglair.taf.mcp.security.ApprovalService;
import com.codinglair.taf.mcp.security.AuthorizationPolicyEngine;
import com.codinglair.taf.mcp.security.InMemoryAuditLog;
import com.codinglair.taf.mcp.security.McpEnforcementService;
import com.codinglair.taf.mcp.security.ResponseRedactor;
import com.codinglair.taf.mcp.tools.McpWorkflowTools;
import com.codinglair.taf.mcp.tools.WorkflowOutcome;
import com.codinglair.taf.mcp.tools.WorkflowResult;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("STDIO authorization delegation")
class StdioAuthorizationTest {
  @TempDir java.nio.file.Path workspace;

  @Test
  @DisplayName("cannot bypass the shared default-deny enforcement service")
  void appliesSharedAuthorization() {
    Clock clock = Clock.systemUTC();
    JobRepository repository = new LocalJobRepository(workspace.resolve("jobs"));
    var redactor = new ResponseRedactor(Set.of());
    var audit = new InMemoryAuditLog(clock, redactor);
    var workflows =
        new McpWorkflowTools(
            workspace,
            new McpEnforcementService(
                new AuthorizationPolicyEngine(List.of()),
                new ApprovalService(clock),
                audit,
                redactor),
            new JobService(repository, clock),
            repository,
            _ -> new WorkflowResult(WorkflowOutcome.SUCCEEDED, "ok", List.of()),
            (_, _) -> new WorkflowResult(WorkflowOutcome.SUCCEEDED, "ok", List.of()),
            Executors.newVirtualThreadPerTaskExecutor(),
            clock,
            redactor);
    var properties = new TafMcpStdioProperties();
    properties.setWorkspaceRoot(workspace);
    try (workflows) {
      var response =
          new StdioWorkflowTools(workflows, properties)
              .execute(
                  new StdioWorkflowRequest(
                      "1.0",
                      "request-1",
                      "execute",
                      "local",
                      "local",
                      30,
                      Optional.of("idempotency-key-1"),
                      Optional.empty(),
                      Map.of("workspace", "")));

      assertThat(response.outcome()).isEqualTo(WorkflowOutcome.DENIED);
      assertThat(repository.findRecoverable()).isEmpty();
      assertThat(audit.events())
          .extracting(event -> event.details().get("transport"))
          .contains("STDIO");
    }
  }
}

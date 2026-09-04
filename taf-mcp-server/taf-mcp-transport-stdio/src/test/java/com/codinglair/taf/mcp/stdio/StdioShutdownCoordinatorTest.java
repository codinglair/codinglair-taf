package com.codinglair.taf.mcp.stdio;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.mcp.jobs.JobRepository;
import com.codinglair.taf.mcp.jobs.JobService;
import com.codinglair.taf.mcp.jobs.JobState;
import com.codinglair.taf.mcp.jobs.LocalJobRepository;
import com.codinglair.taf.mcp.security.ApprovalService;
import com.codinglair.taf.mcp.security.AuthorizationPolicyEngine;
import com.codinglair.taf.mcp.security.InMemoryAuditLog;
import com.codinglair.taf.mcp.security.McpEnforcementService;
import com.codinglair.taf.mcp.security.PolicyRule;
import com.codinglair.taf.mcp.security.ResponseRedactor;
import com.codinglair.taf.mcp.tools.McpWorkflowTools;
import com.codinglair.taf.mcp.tools.WorkflowOutcome;
import com.codinglair.taf.mcp.tools.WorkflowResult;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("STDIO shutdown coordination")
class StdioShutdownCoordinatorTest {
  @TempDir java.nio.file.Path workspace;

  @Test
  @DisplayName("cancels recoverable jobs and releases the workflow executor")
  void cancelsJobsAndExecutor() throws Exception {
    Clock clock = Clock.tick(Clock.systemUTC(), java.time.Duration.ofMillis(1));
    JobRepository repository = new LocalJobRepository(workspace.resolve("jobs"));
    var jobs = new JobService(repository, clock);
    var job = jobs.create("execute", Map.of("project", "local"));
    job =
        repository.save(
            com.codinglair.taf.mcp.jobs.JobStateMachine.transition(
                job, JobState.RUNNING, clock.instant()),
            job.version());
    var redactor = new ResponseRedactor(Set.of());
    var policy =
        new AuthorizationPolicyEngine(
            List.of(
                new PolicyRule(
                    "allow",
                    PolicyRule.Effect.ALLOW,
                    Set.of("*"),
                    Set.of("*"),
                    Set.of("*"),
                    Set.of("*"),
                    Set.of("*"),
                    Set.of("*"),
                    Set.of("*"),
                    false)));
    var executor = Executors.newVirtualThreadPerTaskExecutor();
    var tools =
        new McpWorkflowTools(
            workspace,
            new McpEnforcementService(
                policy,
                new ApprovalService(clock),
                new InMemoryAuditLog(clock, redactor),
                redactor),
            jobs,
            repository,
            _ -> new WorkflowResult(WorkflowOutcome.SUCCEEDED, "ok", List.of()),
            (_, _) -> new WorkflowResult(WorkflowOutcome.SUCCEEDED, "ok", List.of()),
            executor,
            clock,
            redactor);

    new StdioShutdownCoordinator(tools, jobs, repository, clock).close();

    assertThat(repository.find(job.id()).orElseThrow().state()).isEqualTo(JobState.CANCELLED);
    assertThat(executor.isShutdown()).isTrue();
    assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
  }
}

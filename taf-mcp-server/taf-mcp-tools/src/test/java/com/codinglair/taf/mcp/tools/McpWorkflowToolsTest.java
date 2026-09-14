package com.codinglair.taf.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.mcp.jobs.JobRepository;
import com.codinglair.taf.mcp.jobs.JobService;
import com.codinglair.taf.mcp.jobs.LocalJobRepository;
import com.codinglair.taf.mcp.security.ApprovalService;
import com.codinglair.taf.mcp.security.AuthorizationPolicyEngine;
import com.codinglair.taf.mcp.security.CallerIdentity;
import com.codinglair.taf.mcp.security.IdentityKind;
import com.codinglair.taf.mcp.security.InMemoryAuditLog;
import com.codinglair.taf.mcp.security.McpEnforcementService;
import com.codinglair.taf.mcp.security.PolicyRule;
import com.codinglair.taf.mcp.security.ResponseRedactor;
import com.codinglair.taf.mcp.security.Transport;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Coarse-grained MCP workflow tools")
class McpWorkflowToolsTest {
  @TempDir Path workspace;
  private final Clock clock = Clock.systemUTC();
  private final CallerIdentity identity =
      new CallerIdentity("user", Set.of("tester"), "agent", IdentityKind.AGENT);

  @Nested
  @DisplayName("Discovery and validation")
  class Validation {
    @Test
    @DisplayName("discovers the accepted workflow without low-level tools")
    void discoversCoarseGrainedTools() throws Exception {
      try (var fixture = fixture((_, _) -> success())) {
        assertThat(fixture.tools.discover())
            .containsExactly("validate", "compile", "build", "execute", "cancel")
            .noneMatch(name -> Set.of("shell", "sql", "click", "filesystem").contains(name));
      }
    }

    @Test
    @DisplayName("rejects traversal and unsafe selectors with an audit trail")
    void rejectsUnsafeInputs() throws Exception {
      try (var fixture = fixture((_, _) -> success())) {
        var outside = workspace.resolveSibling("outside");
        var response =
            fixture.tools.invoke(request(ToolOperation.EXECUTE, outside, Optional.of("../Suite")));
        assertThat(response.outcome()).isEqualTo(WorkflowOutcome.VALIDATION_FAILED);
        assertThat(fixture.audit.events())
            .extracting(event -> event.type())
            .contains("tool.invoked", "tool.completed");
      }
    }

    @Test
    @DisplayName("returns a structured synchronous environment validation failure")
    void validatesEnvironment() throws Exception {
      try (var fixture =
          fixture(
              (_, _) -> success(),
              _ ->
                  new WorkflowResult(
                      WorkflowOutcome.ENVIRONMENT_FAILED, "environment unavailable", List.of()))) {
        var response =
            fixture.tools.invoke(request(ToolOperation.VALIDATE, workspace, Optional.empty()));
        assertThat(response.status()).isEqualTo(ToolResponse.Status.FAILED);
        assertThat(response.outcome()).isEqualTo(WorkflowOutcome.ENVIRONMENT_FAILED);
      }
    }

    @Test
    @DisplayName("redacts secret canaries from synchronous result summaries")
    void redactsResultSummary() throws Exception {
      try (var fixture =
          fixture(
              (_, _) -> success(),
              _ ->
                  new WorkflowResult(
                      WorkflowOutcome.VALIDATION_FAILED, "contains canary-secret", List.of()))) {
        var response =
            fixture.tools.invoke(request(ToolOperation.VALIDATE, workspace, Optional.empty()));
        assertThat(response.summary())
            .doesNotContain("canary-secret")
            .contains(ResponseRedactor.REDACTED);
      }
    }

    @Test
    @DisplayName("rejects secret canaries before job persistence")
    void rejectsSecretCanaryBeforePersistence() throws Exception {
      try (var fixture = fixture((_, _) -> success())) {
        var response =
            fixture.tools.invoke(
                request(ToolOperation.EXECUTE, workspace, Optional.of("canary-secret")));

        assertThat(response.outcome()).isEqualTo(WorkflowOutcome.VALIDATION_FAILED);
        assertThat(fixture.repository.findRecoverable()).isEmpty();
        assertThat(fixture.audit.events().toString()).doesNotContain("canary-secret");
      }
    }

    @Test
    @DisplayName("audit digest never contains required capability metadata")
    void capabilityDigestContainsNoMetadata() throws Exception {
      try (var fixture = fixture((_, _) -> success())) {
        var request =
            new ToolRequest(
                "request-capability",
                ToolOperation.VALIDATE,
                "project",
                "local",
                workspace,
                Optional.empty(),
                Duration.ofSeconds(30),
                null,
                null,
                null,
                identity,
                Transport.INTERNAL,
                List.of(
                    new RequiredCapability("aws.sqs", "canary-instance", Set.of("await", "send"))));

        fixture.tools.invoke(request);

        assertThat(fixture.audit.events().toString())
            .doesNotContain("requiredCapabilities", "canary-instance", "aws.sqs", "await", "send");
      }
    }
  }

  @Nested
  @DisplayName("Persistent execution")
  class Execution {
    static Stream<Arguments> failures() {
      return Stream.of(
          Arguments.of(ToolOperation.COMPILE, WorkflowOutcome.COMPILE_FAILED),
          Arguments.of(ToolOperation.EXECUTE, WorkflowOutcome.TEST_FAILED),
          Arguments.of(ToolOperation.EXECUTE, WorkflowOutcome.ENVIRONMENT_FAILED),
          Arguments.of(ToolOperation.EXECUTE, WorkflowOutcome.TIMED_OUT),
          Arguments.of(ToolOperation.EXECUTE, WorkflowOutcome.CANCELLED));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("failures")
    @DisplayName("persists distinct terminal workflow outcomes")
    void persistsOutcome(ToolOperation operation, WorkflowOutcome outcome) throws Exception {
      try (var fixture = fixture((_, _) -> new WorkflowResult(outcome, "bounded", List.of()))) {
        var response =
            fixture.tools.invoke(request(operation, workspace, Optional.of("Suite#case")));
        assertThat(response.status()).isEqualTo(ToolResponse.Status.ACCEPTED);
        fixture.executor.shutdown();
        assertThat(fixture.executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        var id =
            new com.codinglair.taf.mcp.jobs.JobId(
                response.jobReference().substring("taf://job/".length()));
        var job = fixture.repository.find(id).orElseThrow();
        assertThat(job.events().getLast().message()).isEqualTo(outcome.name());
        assertThat(job.state().terminal()).isTrue();
      }
    }

    @Test
    @DisplayName("passes the validated selector timeout and environment to the runner")
    void passesSelection() throws Exception {
      var captured = new AtomicReference<ToolRequest>();
      try (var fixture =
          fixture(
              (_, request) -> {
                captured.set(request);
                return success();
              })) {
        var submitted = request(ToolOperation.EXECUTE, workspace, Optional.of("SmokeSuite#login"));
        fixture.tools.invoke(submitted);
        fixture.executor.shutdown();
        assertThat(fixture.executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(captured.get().selector()).contains("SmokeSuite#login");
        assertThat(captured.get().environment()).isEqualTo("local");
        assertThat(captured.get().timeout()).isEqualTo(Duration.ofSeconds(30));
      }
    }

    @Test
    @DisplayName("applies authorization and audit before creating a job")
    void deniesBeforeJobCreation() throws Exception {
      try (var fixture = fixture((_, _) -> success(), _ -> success(), false)) {
        var response =
            fixture.tools.invoke(request(ToolOperation.BUILD, workspace, Optional.empty()));
        assertThat(response.status()).isEqualTo(ToolResponse.Status.FAILED);
        assertThat(response.outcome()).isEqualTo(WorkflowOutcome.DENIED);
        assertThat(fixture.repository.findRecoverable()).isEmpty();
        assertThat(fixture.audit.events())
            .extracting(event -> event.type())
            .contains("tool.invoked", "authorization.decided");
      }
    }

    @Test
    @DisplayName("moves a running job to failed when the runner throws")
    void runnerFailureDoesNotLeaveRunningJob() throws Exception {
      try (var fixture =
          fixture(
              (_, _) -> {
                throw new IllegalStateException("canary-secret");
              })) {
        var response =
            fixture.tools.invoke(request(ToolOperation.BUILD, workspace, Optional.empty()));
        fixture.executor.shutdown();
        assertThat(fixture.executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        var job = fixture.repository.find(jobId(response)).orElseThrow();
        assertThat(job.state()).isEqualTo(com.codinglair.taf.mcp.jobs.JobState.FAILED);
        assertThat(job.events().getLast().message())
            .isEqualTo(WorkflowOutcome.INTERNAL_FAILED.name());
        assertThat(job.toString()).doesNotContain("canary-secret");
      }
    }

    @Test
    @DisplayName("runs concurrent jobs to isolated terminal states")
    void runsConcurrentJobs() throws Exception {
      var started = new CountDownLatch(16);
      var release = new CountDownLatch(1);
      try (var fixture =
          fixture(
              (_, _) -> {
                started.countDown();
                try {
                  release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                  Thread.currentThread().interrupt();
                  throw new IllegalStateException("interrupted", interrupted);
                }
                return success();
              })) {
        var responses =
            java.util.stream.IntStream.range(0, 16)
                .mapToObj(
                    index ->
                        fixture.tools.invoke(
                            request(
                                ToolOperation.EXECUTE, workspace, Optional.of("Suite" + index))))
                .toList();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        release.countDown();
        fixture.executor.shutdown();
        assertThat(fixture.executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(responses)
            .extracting(response -> fixture.repository.find(jobId(response)).orElseThrow().state())
            .allMatch(com.codinglair.taf.mcp.jobs.JobState::terminal);
      }
    }

    @Test
    @DisplayName("closes its owned execution service")
    void closesExecutor() throws Exception {
      var fixture = fixture((_, _) -> success());
      fixture.tools.close();
      assertThat(fixture.executor.isShutdown()).isTrue();
    }

    @Test
    @DisplayName("cancels the durable job when scheduling is rejected")
    void rejectedSchedulingDoesNotLeaveRecoverableJob() throws Exception {
      try (var fixture = fixture((_, _) -> success())) {
        fixture.executor.shutdown();
        var response =
            fixture.tools.invoke(request(ToolOperation.BUILD, workspace, Optional.empty()));
        assertThat(response.outcome()).isEqualTo(WorkflowOutcome.INTERNAL_FAILED);
        assertThat(fixture.repository.findRecoverable()).isEmpty();
      }
    }
  }

  private Fixture fixture(WorkflowRunner runner) throws Exception {
    return fixture(runner, _ -> success());
  }

  private Fixture fixture(WorkflowRunner runner, ProjectValidator validator) throws Exception {
    return fixture(runner, validator, true);
  }

  private Fixture fixture(WorkflowRunner runner, ProjectValidator validator, boolean allow)
      throws Exception {
    JobRepository repository =
        new LocalJobRepository(workspace.resolve("jobs-" + System.nanoTime()));
    var redactor = new ResponseRedactor(Set.of("canary-secret"));
    var audit = new InMemoryAuditLog(clock, redactor);
    var rules =
        allow
            ? List.of(
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
                    false))
            : List.<PolicyRule>of();
    var enforcement =
        new McpEnforcementService(
            new AuthorizationPolicyEngine(rules), new ApprovalService(clock), audit, redactor);
    var executor = Executors.newVirtualThreadPerTaskExecutor();
    var tools =
        new McpWorkflowTools(
            workspace,
            enforcement,
            new JobService(repository, clock),
            repository,
            validator,
            runner,
            executor,
            clock,
            redactor);
    return new Fixture(tools, repository, audit, executor);
  }

  private ToolRequest request(ToolOperation operation, Path path, Optional<String> selector) {
    return new ToolRequest(
        "request-1",
        operation,
        "project",
        "local",
        path,
        selector,
        Duration.ofSeconds(30),
        operation.asynchronous() ? "idempotency-key-1" : null,
        null,
        operation == ToolOperation.CANCEL ? "job-1" : null,
        identity,
        Transport.INTERNAL);
  }

  private static WorkflowResult success() {
    return new WorkflowResult(WorkflowOutcome.SUCCEEDED, "completed", List.of());
  }

  private static com.codinglair.taf.mcp.jobs.JobId jobId(ToolResponse response) {
    return new com.codinglair.taf.mcp.jobs.JobId(
        response.jobReference().substring("taf://job/".length()));
  }

  private record Fixture(
      McpWorkflowTools tools,
      JobRepository repository,
      InMemoryAuditLog audit,
      java.util.concurrent.ExecutorService executor)
      implements AutoCloseable {
    @Override
    public void close() {
      tools.close();
    }
  }
}

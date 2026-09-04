package com.codinglair.taf.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.mcp.jobs.JobId;
import com.codinglair.taf.mcp.security.CallerIdentity;
import com.codinglair.taf.mcp.security.IdentityKind;
import com.codinglair.taf.mcp.security.ResponseRedactor;
import com.codinglair.taf.mcp.security.Transport;
import com.codinglair.taf.mcp.worker.LocalArtifactStore;
import com.codinglair.taf.mcp.worker.LocalExecutionWorker;
import com.codinglair.taf.mcp.worker.WorkerCommand;
import com.codinglair.taf.mcp.worker.WorkerLimits;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Worker workflow runner")
class WorkerWorkflowRunnerTest {
  @TempDir Path temporary;

  @Test
  @DisplayName("resolves structured selection to an administrator allowlisted workflow")
  void resolvesSelectedWorkflow() throws Exception {
    var executable = javaExecutable();
    var source = Files.createDirectory(temporary.resolve("source"));
    var worker =
        new LocalExecutionWorker(
            Map.of(
                "selected-smoke",
                new WorkerCommand("selected-smoke", List.of(executable.toString(), "-version"))),
            WorkerLimits.DEFAULT,
            temporary.resolve("execution"),
            new LocalArtifactStore(temporary.resolve("artifacts")),
            new ResponseRedactor(Set.of("canary-secret")),
            Clock.systemUTC());
    var resolved = new AtomicReference<String>();
    var runner =
        new WorkerWorkflowRunner(
            worker,
            () -> false,
            (operation, environment, selector) -> {
              resolved.set(operation.action() + ":" + environment + ":" + selector.orElse("all"));
              return "selected-smoke";
            });
    var request =
        new ToolRequest(
            "request-1",
            ToolOperation.EXECUTE,
            "project",
            "local",
            source,
            Optional.of("SmokeSuite"),
            Duration.ofSeconds(30),
            "idempotency-key-1",
            null,
            null,
            new CallerIdentity("user", Set.of("tester"), "agent", IdentityKind.AGENT),
            Transport.INTERNAL);

    var result = runner.run(new JobId("job-1"), request);

    assertThat(result.outcome()).isEqualTo(WorkflowOutcome.SUCCEEDED);
    assertThat(resolved).hasValue("execute:local:SmokeSuite");
  }

  private static Path javaExecutable() {
    var bin = Path.of(System.getProperty("java.home"), "bin");
    var windows = bin.resolve("java.exe");
    return Files.isRegularFile(windows) ? windows : bin.resolve("java");
  }
}

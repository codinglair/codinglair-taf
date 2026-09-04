package com.codinglair.taf.mcp.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.codinglair.taf.mcp.jobs.Job;
import com.codinglair.taf.mcp.jobs.JobId;
import com.codinglair.taf.mcp.jobs.JobService;
import com.codinglair.taf.mcp.jobs.JobState;
import com.codinglair.taf.mcp.jobs.LocalJobRepository;
import com.codinglair.taf.mcp.security.ResponseRedactor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Isolated local execution worker")
class LocalExecutionWorkerTest {
  private static final Pattern CHILD_PID = Pattern.compile("(?m)^CHILD=(\\d+)\\s*$");

  @TempDir Path temporary;
  private Path source;
  private Path executionRoot;
  private Path artifactRoot;

  @BeforeEach
  void prepare() throws IOException {
    source = Files.createDirectory(temporary.resolve("source"));
    executionRoot = Files.createDirectory(temporary.resolve("execution"));
    artifactRoot = Files.createDirectory(temporary.resolve("artifacts"));
    Files.writeString(source.resolve("dirty.txt"), "user-change");
  }

  @Nested
  @DisplayName("Workspace and artifact isolation")
  class WorkspaceIsolation {
    @Test
    @DisplayName("executes only in a disposable copy and preserves the source working tree")
    void preservesDirtyTree() throws Exception {
      var worker = worker("success", limits(4096));

      var result =
          worker.execute(
              request("success", Duration.ofSeconds(5), List.of("target/result.txt")),
              CancellationToken.NEVER);

      assertThat(result.status()).isEqualTo(WorkerStatus.SUCCEEDED);
      assertThat(result.outputSummary()).contains("build and test succeeded");
      assertThat(result.artifacts())
          .singleElement()
          .satisfies(
              entry -> {
                assertThat(entry.relativePath()).isEqualTo("target/result.txt");
                assertThat(entry.sha256()).hasSize(64);
                assertThat(entry.reference().uri()).startsWith("taf://artifact/");
              });
      assertThat(Files.readString(source.resolve("dirty.txt"))).isEqualTo("user-change");
      assertThat(executionRoot).isEmptyDirectory();
    }

    @Test
    @DisplayName("rejects artifact traversal outside the copied workspace")
    void rejectsTraversal() throws Exception {
      var worker = worker("success", limits(4096));

      var error =
          assertThrows(
              WorkerExecutionException.class,
              () ->
                  worker.execute(
                      request("success", Duration.ofSeconds(5), List.of("../outside.txt")),
                      CancellationToken.NEVER));

      assertThat(error.code()).isEqualTo("PATH_ESCAPE");
      assertThat(executionRoot).isEmptyDirectory();
    }

    @Test
    @DisplayName("rejects symbolic links in the declared workspace")
    void rejectsWorkspaceLinks() throws Exception {
      var outside = Files.writeString(temporary.resolve("outside.txt"), "outside");
      try {
        Files.createSymbolicLink(source.resolve("escape"), outside);
      } catch (UnsupportedOperationException | IOException error) {
        org.junit.jupiter.api.Assumptions.assumeTrue(
            false, "Symbolic links unavailable: " + error.getClass().getSimpleName());
      }

      var error =
          assertThrows(
              WorkerExecutionException.class,
              () ->
                  worker("success", limits(4096))
                      .execute(
                          request("success", Duration.ofSeconds(5), List.of()),
                          CancellationToken.NEVER));

      assertThat(error.code()).isEqualTo("PATH_ESCAPE");
      assertThat(Files.readString(outside)).isEqualTo("outside");
    }
  }

  @Nested
  @DisplayName("Process lifecycle and output safety")
  class ProcessLifecycle {
    @Test
    @DisplayName("times out and forcibly terminates the complete descendant tree")
    void timeoutKillsDescendants() {
      var result =
          assertTimeoutPreemptively(
              Duration.ofSeconds(10),
              () ->
                  worker("tree", limits(4096))
                      .execute(
                          request("tree", Duration.ofMillis(250), List.of()),
                          CancellationToken.NEVER));

      assertThat(result.status()).isEqualTo(WorkerStatus.TIMED_OUT);
      var childPidMatcher = CHILD_PID.matcher(result.outputSummary());
      assertThat(childPidMatcher.find())
          .as("worker output contains the descendant PID marker")
          .isTrue();
      var childPid = Long.parseLong(childPidMatcher.group(1));
      assertThat(ProcessHandle.of(childPid)).isEmpty();
      assertThat(executionRoot).isEmptyDirectory();
    }

    @Test
    @DisplayName("cancellation terminates descendants and cleans the disposable workspace")
    void cancellationKillsDescendants() {
      var cancellationAt = System.nanoTime() + Duration.ofMillis(250).toNanos();

      var result =
          assertTimeoutPreemptively(
              Duration.ofSeconds(10),
              () ->
                  worker("tree", limits(4096))
                      .execute(
                          request("tree", Duration.ofSeconds(20), List.of()),
                          () -> System.nanoTime() >= cancellationAt));

      assertThat(result.status()).isEqualTo(WorkerStatus.CANCELLED);
      assertThat(executionRoot).isEmptyDirectory();
    }

    @Test
    @DisplayName("bounds returned process output")
    void boundsOutput() throws Exception {
      var result =
          worker("large", limits(128))
              .execute(request("large", Duration.ofSeconds(5), List.of()), CancellationToken.NEVER);

      assertThat(result.outputSummary()).hasSize(128);
      assertThat(result.outputTruncated()).isTrue();
    }

    @Test
    @DisplayName("redacts output and refuses secret-bearing artifacts")
    void blocksSecretLeaks() throws Exception {
      var error =
          assertThrows(
              WorkerExecutionException.class,
              () ->
                  worker("secret", limits(4096))
                      .execute(
                          request("secret", Duration.ofSeconds(30), List.of("target/result.txt")),
                          CancellationToken.NEVER));

      assertThat(error.code()).isEqualTo("ARTIFACT_REJECTED");
      assertThat(artifactRoot).isEmptyDirectory();
    }

    @Test
    @DisplayName("denies workflows absent from the administrator allowlist")
    void deniesUnknownWorkflow() throws Exception {
      var error =
          assertThrows(
              WorkerExecutionException.class,
              () ->
                  worker("success", limits(4096))
                      .execute(
                          request("unknown", Duration.ofSeconds(5), List.of()),
                          CancellationToken.NEVER));

      assertThat(error.code()).isEqualTo("WORKFLOW_DENIED");
    }
  }

  @Nested
  @DisplayName("Durable job coordination")
  class DurableJobs {
    @Test
    @DisplayName("attaches controlled artifacts and acknowledges successful terminal state")
    void recordsSuccess() throws Exception {
      var repository = new LocalJobRepository(temporary.resolve("jobs-success"));
      var request = request("success", Duration.ofSeconds(5), List.of("target/result.txt"));
      repository.create(Job.queued(request.jobId(), "build", Map.of(), Instant.now()));

      var completed =
          new JobExecutionCoordinator(
                  repository, worker("success", limits(4096)), Clock.systemUTC())
              .execute(request);

      assertThat(completed.state()).isEqualTo(JobState.SUCCEEDED);
      assertThat(completed.resultReferences())
          .singleElement()
          .satisfies(reference -> assertThat(reference.uri()).startsWith("taf://artifact/"));
    }

    @Test
    @DisplayName("uses portable storage paths for opaque job identifiers")
    void supportsPortableOpaqueJobIds() throws Exception {
      var request =
          new WorkerRequest(
              WorkerProtocol.VERSION,
              new JobId("project:job:42"),
              "success",
              source,
              Duration.ofSeconds(5),
              List.of("target/result.txt"),
              "correlation-1");

      var result = worker("success", limits(4096)).execute(request, CancellationToken.NEVER);

      assertThat(result.status()).isEqualTo(WorkerStatus.SUCCEEDED);
      assertThat(result.artifacts())
          .singleElement()
          .satisfies(artifact -> assertThat(artifact.reference().uri()).contains("project:job:42"));
    }

    @Test
    @DisplayName("acknowledges cancellation before launch without invoking the workflow")
    void acknowledgesPreLaunchCancellation() throws Exception {
      var repository = new LocalJobRepository(temporary.resolve("jobs-cancel"));
      var request = request("success", Duration.ofSeconds(5), List.of());
      repository.create(Job.queued(request.jobId(), "build", Map.of(), Instant.now()));
      new JobService(repository, Clock.systemUTC()).cancel(request.jobId());

      var completed =
          new JobExecutionCoordinator(
                  repository, worker("success", limits(4096)), Clock.systemUTC())
              .execute(request);

      assertThat(completed.state()).isEqualTo(JobState.CANCELLED);
      assertThat(Files.readString(source.resolve("dirty.txt"))).isEqualTo("user-change");
    }
  }

  private LocalExecutionWorker worker(String mode, WorkerLimits limits) throws IOException {
    var command =
        new WorkerCommand(
            mode,
            List.of(
                javaExecutable(),
                "-cp",
                System.getProperty("java.class.path"),
                WorkerProcessFixture.class.getName(),
                mode));
    return new LocalExecutionWorker(
        Map.of(mode, command),
        limits,
        executionRoot,
        new LocalArtifactStore(artifactRoot),
        new ResponseRedactor(Set.of("worker-canary-43f1")),
        Clock.systemUTC());
  }

  private WorkerRequest request(String workflow, Duration timeout, List<String> artifactPaths) {
    return new WorkerRequest(
        WorkerProtocol.VERSION,
        new JobId("job-12345678"),
        workflow,
        source,
        timeout,
        artifactPaths,
        "correlation-1");
  }

  private static WorkerLimits limits(long outputBytes) {
    return new WorkerLimits(Duration.ofSeconds(30), outputBytes, 1_048_576, 10, 100, 1_048_576);
  }

  private static String javaExecutable() {
    var suffix = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
    return Path.of(System.getProperty("java.home"), "bin", suffix).toAbsolutePath().toString();
  }
}

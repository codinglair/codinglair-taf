package com.codinglair.taf.mcp.worker;

import com.codinglair.taf.mcp.security.ResponseRedactor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Executes fixed workflows in disposable copied workspaces and always tears down process trees. */
public final class LocalExecutionWorker {
  private final Map<String, WorkerCommand> commands;
  private final WorkerLimits limits;
  private final WorkspacePreparer workspaces;
  private final ArtifactStore artifacts;
  private final ResponseRedactor redactor;
  private final Clock clock;

  public LocalExecutionWorker(
      Map<String, WorkerCommand> commands,
      WorkerLimits limits,
      Path executionRoot,
      ArtifactStore artifacts,
      ResponseRedactor redactor,
      Clock clock) {
    this.commands = Map.copyOf(commands);
    this.limits = Objects.requireNonNull(limits, "limits");
    this.workspaces = new WorkspacePreparer(executionRoot, limits);
    this.artifacts = Objects.requireNonNull(artifacts, "artifacts");
    this.redactor = Objects.requireNonNull(redactor, "redactor");
    this.clock = Objects.requireNonNull(clock, "clock");
    if (!this.commands.keySet().stream()
        .allMatch(name -> name.equals(this.commands.get(name).workflow()))) {
      throw new IllegalArgumentException("Command allowlist keys must match workflow names");
    }
  }

  public WorkerResult execute(WorkerRequest request, CancellationToken cancellation) {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(cancellation, "cancellation");
    var command = commands.get(request.workflow());
    if (command == null) {
      throw new WorkerExecutionException("WORKFLOW_DENIED", "Workflow is not allowlisted");
    }
    if (request.timeout().isZero()
        || request.timeout().isNegative()
        || request.timeout().compareTo(limits.maximumTimeout()) > 0) {
      throw new WorkerExecutionException("TIMEOUT_LIMIT", "Timeout exceeds administrative limit");
    }
    Path workspace = null;
    Process process = null;
    var started = clock.instant();
    try {
      workspace = workspaces.prepare(request);
      var output = new BoundedOutput(limits.maximumOutputBytes());
      process =
          new ProcessBuilder(command.argv())
              .directory(workspace.toFile())
              .redirectErrorStream(true)
              .start();
      var runningProcess = process;
      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        var drain =
            executor.submit(
                () -> {
                  try {
                    output.drain(runningProcess.getInputStream());
                  } catch (IOException error) {
                    throw new UncheckedIOException(error);
                  }
                });
        var deadline = System.nanoTime() + request.timeout().toNanos();
        WorkerStatus status = null;
        while (process.isAlive()) {
          if (cancellation.cancellationRequested()) {
            status = WorkerStatus.CANCELLED;
            terminateTree(process);
            break;
          }
          if (System.nanoTime() >= deadline) {
            status = WorkerStatus.TIMED_OUT;
            terminateTree(process);
            break;
          }
          process.waitFor(25, TimeUnit.MILLISECONDS);
        }
        drain.get(10, TimeUnit.SECONDS);
        var exit = process.isAlive() ? -1 : process.exitValue();
        if (status == null) {
          status = exit == 0 ? WorkerStatus.SUCCEEDED : WorkerStatus.FAILED;
        }
        var manifest = collectArtifacts(request, workspace);
        return new WorkerResult(
            WorkerProtocol.VERSION,
            status,
            exit,
            output.sanitized(redactor),
            output.truncated(),
            Duration.between(started, clock.instant()),
            manifest);
      }
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      if (process != null) {
        terminateTree(process);
      }
      throw new WorkerExecutionException("INTERRUPTED", "Worker execution was interrupted", error);
    } catch (WorkerExecutionException error) {
      throw error;
    } catch (Exception error) {
      if (process != null) {
        terminateTree(process);
      }
      throw new WorkerExecutionException(
          "EXECUTION_FAILED", "Worker execution failed safely", error);
    } finally {
      try {
        WorkspacePreparer.delete(workspace);
      } catch (IOException error) {
        throw new UncheckedIOException("Worker workspace cleanup failed", error);
      }
    }
  }

  private java.util.List<ArtifactManifestEntry> collectArtifacts(
      WorkerRequest request, Path workspace) throws IOException {
    if (request.artifactPaths().size() > limits.maximumArtifacts()) {
      throw new WorkerExecutionException("ARTIFACT_LIMIT", "Artifact count exceeds limit");
    }
    var manifest = new ArrayList<ArtifactManifestEntry>();
    long total = 0;
    for (var relative : request.artifactPaths()) {
      var source = WorkspacePreparer.contained(workspace, relative);
      if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
        continue;
      }
      if (Files.isSymbolicLink(source) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
        throw new WorkerExecutionException("INVALID_ARTIFACT", "Artifact must be a regular file");
      }
      var size = Files.size(source);
      total += size;
      if (total > limits.maximumArtifactBytes()) {
        throw new WorkerExecutionException("ARTIFACT_LIMIT", "Artifact bytes exceed limit");
      }
      var artifactText = Files.readString(source, StandardCharsets.ISO_8859_1);
      if (!artifactText.equals(redactor.redact(artifactText))) {
        throw new WorkerExecutionException(
            "ARTIFACT_REJECTED", "Artifact failed sensitive-data inspection");
      }
      var reference = artifacts.store(request.jobId(), relative, source);
      manifest.add(
          new ArtifactManifestEntry(relative.replace('\\', '/'), size, sha256(source), reference));
    }
    return java.util.List.copyOf(manifest);
  }

  private static String sha256(Path file) throws IOException {
    try (var input = Files.newInputStream(file)) {
      var digest = MessageDigest.getInstance("SHA-256");
      input.transferTo(
          new java.io.OutputStream() {
            @Override
            public void write(int value) {
              digest.update((byte) value);
            }

            @Override
            public void write(byte[] value, int offset, int length) {
              digest.update(value, offset, length);
            }
          });
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }

  private static void terminateTree(Process process) {
    var descendants = process.descendants().toList().reversed();
    descendants.forEach(ProcessHandle::destroy);
    process.destroy();
    awaitExit(process.toHandle(), descendants);
    descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
    if (process.isAlive()) {
      process.destroyForcibly();
    }
    awaitExit(process.toHandle(), descendants);
  }

  private static void awaitExit(ProcessHandle root, java.util.List<ProcessHandle> descendants) {
    var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    while ((root.isAlive() || descendants.stream().anyMatch(ProcessHandle::isAlive))
        && System.nanoTime() < deadline) {
      Thread.onSpinWait();
    }
  }
}

package com.codinglair.taf.mcp.worker;

import com.codinglair.taf.mcp.jobs.JobId;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Immutable request selecting an administrator-defined workflow, never a shell command. */
public record WorkerRequest(
    String protocolVersion,
    JobId jobId,
    String workflow,
    Path sourceWorkspace,
    Duration timeout,
    List<String> artifactPaths,
    String correlationId) {
  public WorkerRequest {
    if (!WorkerProtocol.VERSION.equals(protocolVersion)) {
      throw new IllegalArgumentException("Unsupported worker protocol version");
    }
    Objects.requireNonNull(jobId, "jobId");
    workflow = text(workflow, "workflow", 64);
    sourceWorkspace =
        Objects.requireNonNull(sourceWorkspace, "sourceWorkspace").toAbsolutePath().normalize();
    Objects.requireNonNull(timeout, "timeout");
    artifactPaths = List.copyOf(Objects.requireNonNull(artifactPaths, "artifactPaths"));
    artifactPaths.forEach(path -> text(path, "artifactPath", 512));
    correlationId = text(correlationId, "correlationId", 128);
  }

  private static String text(String value, String name, int maximum) {
    if (value == null
        || value.isBlank()
        || value.length() > maximum
        || value.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException(name + " must be nonblank, bounded, and free of controls");
    }
    return value;
  }
}

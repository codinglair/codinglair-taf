package com.codinglair.taf.mcp.worker;

import java.time.Duration;
import java.util.List;

/** Bounded execution summary; large output and artifacts are returned only by reference. */
public record WorkerResult(
    String protocolVersion,
    WorkerStatus status,
    int exitCode,
    String outputSummary,
    boolean outputTruncated,
    Duration duration,
    List<ArtifactManifestEntry> artifacts) {
  public WorkerResult {
    artifacts = List.copyOf(artifacts);
  }
}

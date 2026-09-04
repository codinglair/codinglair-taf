package com.codinglair.taf.mcp.worker;

import java.time.Duration;

/** Administrator-controlled ceilings applied to every worker request. */
public record WorkerLimits(
    Duration maximumTimeout,
    long maximumOutputBytes,
    long maximumArtifactBytes,
    int maximumArtifacts,
    int maximumWorkspaceFiles,
    long maximumWorkspaceBytes) {
  public static final WorkerLimits DEFAULT =
      new WorkerLimits(Duration.ofMinutes(30), 1_048_576, 52_428_800, 100, 50_000, 1_073_741_824);

  public WorkerLimits {
    if (maximumTimeout == null
        || maximumTimeout.isZero()
        || maximumTimeout.isNegative()
        || maximumOutputBytes < 1
        || maximumArtifactBytes < 1
        || maximumArtifacts < 1
        || maximumWorkspaceFiles < 1
        || maximumWorkspaceBytes < 1) {
      throw new IllegalArgumentException("Worker limits must be positive");
    }
  }
}

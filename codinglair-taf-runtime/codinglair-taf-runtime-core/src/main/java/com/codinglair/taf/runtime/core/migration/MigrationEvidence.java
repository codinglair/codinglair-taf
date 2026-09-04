package com.codinglair.taf.runtime.core.migration;

import java.time.Duration;

/** Sanitized materialization evidence for one version in a logical target history. */
public record MigrationEvidence(
    String targetName,
    String version,
    String checksum,
    MigrationOutcome outcome,
    Duration duration) {}

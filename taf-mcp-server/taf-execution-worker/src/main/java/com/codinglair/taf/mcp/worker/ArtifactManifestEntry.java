package com.codinglair.taf.mcp.worker;

import com.codinglair.taf.mcp.jobs.JobReference;

public record ArtifactManifestEntry(
    String relativePath, long size, String sha256, JobReference reference) {}

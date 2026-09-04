package com.codinglair.taf.mcp.resources;

import java.time.Instant;

/** Content-free evidence metadata; binary or large content remains behind its controlled URI. */
public record EvidenceMetadata(
    String jobId,
    String evidenceId,
    String uri,
    String mediaType,
    long sizeBytes,
    String sha256,
    Instant createdAt) {
  public EvidenceMetadata {
    jobId = requireText(jobId, "jobId");
    evidenceId = requireText(evidenceId, "evidenceId");
    uri = requireText(uri, "uri");
    mediaType = requireText(mediaType, "mediaType");
    sha256 = requireText(sha256, "sha256");
    if (!uri.startsWith("taf://evidence/") && !uri.startsWith("taf://artifact/")) {
      throw new IllegalArgumentException("evidence URI must be controlled");
    }
    if (sizeBytes < 0 || createdAt == null) {
      throw new IllegalArgumentException("sizeBytes and createdAt are required");
    }
  }

  public String id() {
    return jobId + "/" + evidenceId;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value;
  }
}

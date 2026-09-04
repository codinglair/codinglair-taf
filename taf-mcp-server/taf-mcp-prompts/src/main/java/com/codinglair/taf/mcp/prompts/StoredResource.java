package com.codinglair.taf.mcp.prompts;

import com.codinglair.taf.mcp.resources.EvidenceAccessScope;
import java.nio.charset.StandardCharsets;

/** Authorized report or evidence content stored behind a controlled URI. */
public record StoredResource(
    EvidenceAccessScope access,
    String jobId,
    String resourceId,
    String uri,
    String mediaType,
    String content,
    boolean redacted) {
  public static final int MAX_STORED_BYTES = 4 * 1024 * 1024;

  public StoredResource {
    if (access == null) {
      throw new IllegalArgumentException("access is required");
    }
    jobId = TextBounds.requireIdentifier(jobId, "jobId");
    resourceId = TextBounds.requireIdentifier(resourceId, "resourceId");
    uri = TextBounds.requireText(uri, "uri", 2048);
    mediaType = TextBounds.requireText(mediaType, "mediaType", 128);
    content = content == null ? "" : content;
    if ((!uri.startsWith("taf://report/") && !uri.startsWith("taf://evidence/"))
        || content.getBytes(StandardCharsets.UTF_8).length > MAX_STORED_BYTES) {
      throw new IllegalArgumentException("resource is not controlled or exceeds storage bound");
    }
  }

  public int sizeBytes() {
    return content.getBytes(StandardCharsets.UTF_8).length;
  }
}

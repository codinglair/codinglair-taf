package com.codinglair.taf.mcp.resources;

import java.nio.charset.StandardCharsets;

/** Versioned, bounded documentation content. */
public record DocumentationResource(
    String id, String version, String uri, String mediaType, String content, int sizeBytes) {
  public static final int MAXIMUM_CONTENT_BYTES = 64 * 1024;

  public DocumentationResource {
    if (id == null || id.isBlank() || version == null || version.isBlank()) {
      throw new IllegalArgumentException("documentation id and version are required");
    }
    if (uri == null || !uri.startsWith("taf://documentation/")) {
      throw new IllegalArgumentException("documentation URI must be controlled");
    }
    if (mediaType == null || mediaType.isBlank() || content == null) {
      throw new IllegalArgumentException("documentation mediaType and content are required");
    }
    var actualSize = content.getBytes(StandardCharsets.UTF_8).length;
    if (actualSize > MAXIMUM_CONTENT_BYTES || sizeBytes != actualSize) {
      throw new IllegalArgumentException("documentation content size is invalid");
    }
  }
}

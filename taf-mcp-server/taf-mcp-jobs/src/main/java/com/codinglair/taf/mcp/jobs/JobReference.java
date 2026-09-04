package com.codinglair.taf.mcp.jobs;

import java.util.Objects;
import java.util.regex.Pattern;

/** Controlled reference to a result or large artifact; content is never stored in the job. */
public record JobReference(String uri, String mediaType) {
  private static final Pattern URI =
      Pattern.compile("taf://(?:artifact|evidence|report|job|documentation)/\\S{1,2030}");

  public JobReference {
    Objects.requireNonNull(uri, "uri");
    Objects.requireNonNull(mediaType, "mediaType");
    if (!URI.matcher(uri).matches() || uri.length() > 2048) {
      throw new IllegalArgumentException("Result must be a bounded controlled taf:// reference");
    }
    if (mediaType.isBlank() || mediaType.length() > 128) {
      throw new IllegalArgumentException("Media type must contain 1 to 128 characters");
    }
  }
}

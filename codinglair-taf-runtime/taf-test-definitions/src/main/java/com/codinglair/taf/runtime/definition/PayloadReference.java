package com.codinglair.taf.runtime.definition;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/** Technology-neutral metadata for immutable binary content stored outside a definition. */
public record PayloadReference(
    String logicalId, URI location, String checksum, String mediaType, long size, String version) {
  public PayloadReference {
    logicalId = text(logicalId, "logicalId");
    location = Objects.requireNonNull(location, "location");
    if (!location.isAbsolute() && location.getPath().contains("..")) {
      throw new IllegalArgumentException("relative payload location may not traverse directories");
    }
    checksum = text(checksum, "checksum").toLowerCase(Locale.ROOT);
    if (!checksum.matches("sha256:[0-9a-f]{64}")) {
      throw new IllegalArgumentException("checksum must be a SHA-256 checksum");
    }
    mediaType = text(mediaType, "mediaType").toLowerCase(Locale.ROOT);
    if (!mediaType.matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+")) {
      throw new IllegalArgumentException("mediaType must be a valid type/subtype");
    }
    if (size < 0) throw new IllegalArgumentException("size must not be negative");
    version = text(version, "version");
  }

  private static String text(String value, String name) {
    String result = Objects.requireNonNull(value, name).trim();
    if (result.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
    return result;
  }
}

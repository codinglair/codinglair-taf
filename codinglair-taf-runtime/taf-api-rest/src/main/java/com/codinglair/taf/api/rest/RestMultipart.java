package com.codinglair.taf.api.rest;

import java.util.Arrays;
import java.util.Objects;

/** Immutable multipart value. */
public record RestMultipart(
    String controlName, String fileName, String contentType, byte[] content) {
  public RestMultipart {
    if (controlName == null || controlName.isBlank())
      throw new IllegalArgumentException("Multipart control name must not be blank");
    Objects.requireNonNull(content, "content");
    content = content.clone();
  }

  @Override
  public byte[] content() {
    return content.clone();
  }

  @Override
  public boolean equals(Object candidate) {
    return candidate instanceof RestMultipart other
        && Objects.equals(controlName, other.controlName)
        && Objects.equals(fileName, other.fileName)
        && Objects.equals(contentType, other.contentType)
        && Arrays.equals(content, other.content);
  }

  @Override
  public int hashCode() {
    return 31 * Objects.hash(controlName, fileName, contentType) + Arrays.hashCode(content);
  }
}

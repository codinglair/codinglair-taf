package com.codinglair.taf.api.soap;

import java.util.Arrays;
import java.util.Objects;

public record SoapAttachment(String contentId, String contentType, byte[] content) {
  public SoapAttachment {
    if (contentId == null || contentId.isBlank())
      throw new IllegalArgumentException("Attachment content ID must not be blank");
    if (contentType == null || contentType.isBlank())
      throw new IllegalArgumentException("Attachment content type must not be blank");
    Objects.requireNonNull(content, "content");
    content = content.clone();
  }

  @Override
  public byte[] content() {
    return content.clone();
  }

  @Override
  public boolean equals(Object value) {
    return value instanceof SoapAttachment other
        && contentId.equals(other.contentId)
        && contentType.equals(other.contentType)
        && Arrays.equals(content, other.content);
  }

  @Override
  public int hashCode() {
    return 31 * Objects.hash(contentId, contentType) + Arrays.hashCode(content);
  }
}

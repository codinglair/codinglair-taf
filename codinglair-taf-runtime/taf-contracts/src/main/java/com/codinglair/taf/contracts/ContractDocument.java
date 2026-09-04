package com.codinglair.taf.contracts;

import java.util.Objects;

/** Immutable, explicitly versioned contract input. */
public record ContractDocument(
    ContractFormat format, String schemaVersion, String assetVersion, String content) {
  public ContractDocument {
    format = Objects.requireNonNull(format, "format must not be null");
    schemaVersion = requireText(schemaVersion, "schemaVersion");
    assetVersion = requireText(assetVersion, "assetVersion");
    content = requireText(content, "content");
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(name + " must not be blank");
    return value;
  }
}

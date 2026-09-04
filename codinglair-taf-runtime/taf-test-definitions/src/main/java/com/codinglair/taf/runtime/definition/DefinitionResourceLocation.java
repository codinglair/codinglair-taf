package com.codinglair.taf.runtime.definition;

import java.nio.file.Path;
import java.util.Objects;

/** Typed classpath or external-file location. */
public record DefinitionResourceLocation(Kind kind, String value) {
  public enum Kind {
    CLASSPATH,
    FILE
  }

  public DefinitionResourceLocation {
    kind = Objects.requireNonNull(kind, "kind");
    value = Objects.requireNonNull(value, "value").trim();
    if (value.isEmpty()) throw new IllegalArgumentException("resource location must not be blank");
  }

  public static DefinitionResourceLocation classpath(String resource) {
    String normalized = Objects.requireNonNull(resource, "resource").replace('\\', '/');
    while (normalized.startsWith("/")) normalized = normalized.substring(1);
    if (normalized.isBlank() || normalized.contains("..")) {
      throw new IllegalArgumentException(
          "Classpath resource must be normalized and may not traverse directories");
    }
    return new DefinitionResourceLocation(Kind.CLASSPATH, normalized);
  }

  public static DefinitionResourceLocation file(Path path) {
    return new DefinitionResourceLocation(
        Kind.FILE, Objects.requireNonNull(path, "path").toAbsolutePath().normalize().toString());
  }
}

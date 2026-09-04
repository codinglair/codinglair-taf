package com.codinglair.taf.runtime.definition;

import java.nio.file.Path;
import java.util.Objects;

/** Configuration for a writable, Git-friendly JSON or YAML definition document. */
public record FileTestDefinitionConfiguration(
    Path file, DefinitionFileFormat format, RepositoryAuthority authority) {
  public FileTestDefinitionConfiguration {
    file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    format = Objects.requireNonNull(format, "format");
    authority = Objects.requireNonNull(authority, "authority");
  }
}

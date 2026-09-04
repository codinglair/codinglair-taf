package com.codinglair.taf.runtime.file;

import java.nio.file.Path;
import java.util.Objects;

/** Request to compare two files relative to a controller sandbox. */
public record FileComparisonRequest(
    Path expected, Path actual, FileFormat format, FileComparisonOptions options) {
  public FileComparisonRequest {
    Objects.requireNonNull(expected, "expected");
    Objects.requireNonNull(actual, "actual");
    Objects.requireNonNull(format, "format");
    options = Objects.requireNonNullElseGet(options, FileComparisonOptions::defaults);
  }
}

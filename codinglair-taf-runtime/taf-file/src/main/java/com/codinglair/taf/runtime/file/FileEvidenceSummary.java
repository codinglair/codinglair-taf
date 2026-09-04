package com.codinglair.taf.runtime.file;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Bounded, content-free evidence suitable for neutral reporting. */
public record FileEvidenceSummary(
    String format,
    boolean matched,
    long expectedSize,
    long actualSize,
    String expectedSha256,
    String actualSha256,
    Set<String> ignoredFields,
    String numericTolerance,
    List<String> differences) {
  public FileEvidenceSummary {
    Objects.requireNonNull(format);
    Objects.requireNonNull(expectedSha256);
    Objects.requireNonNull(actualSha256);
    ignoredFields = Set.copyOf(ignoredFields);
    differences = List.copyOf(differences);
  }

  public String asText() {
    StringBuilder sb = new StringBuilder();
    sb.append("format=").append(format).append("\n");
    sb.append("matched=").append(matched).append("\n");
    sb.append("expectedSize=").append(expectedSize).append("\n");
    sb.append("actualSize=").append(actualSize).append("\n");
    sb.append("expectedSha256=").append(expectedSha256).append("\n");
    sb.append("actualSha256=").append(actualSha256).append("\n");
    sb.append("ignoredFields=").append(ignoredFields.stream().sorted().toList()).append("\n");
    sb.append("numericTolerance=").append(numericTolerance).append("\n");
    sb.append("differences=").append(differences);
    return sb.toString();
  }
}

package com.codinglair.taf.runtime.file;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable deterministic comparison options. Ignored fields use normalized field paths. */
public record FileComparisonOptions(
    Set<String> ignoredFields,
    BigDecimal numericTolerance,
    Charset charset,
    char csvDelimiter,
    List<Integer> fixedWidths,
    int maximumDifferences) {
  public FileComparisonOptions {
    ignoredFields = ignoredFields == null ? Set.of() : Set.copyOf(ignoredFields);
    numericTolerance = Objects.requireNonNullElse(numericTolerance, BigDecimal.ZERO);
    charset = Objects.requireNonNullElse(charset, StandardCharsets.UTF_8);
    fixedWidths = fixedWidths == null ? List.of() : List.copyOf(fixedWidths);
    if (numericTolerance.signum() < 0)
      throw new IllegalArgumentException("numericTolerance must not be negative");
    if (maximumDifferences < 1)
      throw new IllegalArgumentException("maximumDifferences must be positive");
    if (fixedWidths.stream().anyMatch(width -> width == null || width < 1))
      throw new IllegalArgumentException("fixedWidths must contain positive widths");
  }

  public static FileComparisonOptions defaults() {
    return new FileComparisonOptions(
        Set.of(), BigDecimal.ZERO, StandardCharsets.UTF_8, ',', List.of(), 20);
  }
}

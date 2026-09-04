package com.codinglair.taf.runtime.file;

import java.util.List;

/** Deterministic validation outcome. */
public record FileValidationResult(
    boolean matched, List<String> differences, FileEvidenceSummary evidence) {
  public FileValidationResult {
    differences = List.copyOf(differences);
  }
}

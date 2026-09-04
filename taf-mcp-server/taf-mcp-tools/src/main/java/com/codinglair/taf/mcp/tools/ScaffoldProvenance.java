package com.codinglair.taf.mcp.tools;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public record ScaffoldProvenance(
    String operationId,
    String template,
    String templateVersion,
    Instant createdAt,
    List<CreatedFile> files) {
  public ScaffoldProvenance {
    files = List.copyOf(files);
    if (files.isEmpty()) {
      throw new IllegalArgumentException("scaffold provenance requires at least one created file");
    }
  }

  public record CreatedFile(Path path, String sha256, ScaffoldAsset.Kind kind) {
    public CreatedFile {
      if (path == null || path.isAbsolute() || sha256 == null || sha256.isBlank() || kind == null) {
        throw new IllegalArgumentException(
            "created-file path, digest, and asset kind are required");
      }
    }
  }
}

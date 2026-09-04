package com.codinglair.taf.mcp.tools;

import java.nio.file.Path;

/**
 * One immutable, relative-path asset supplied by a governed scaffold template.
 *
 * @param path confined path relative to the scaffold workspace
 * @param content immutable file content
 * @param kind whether the asset changes project content or dependency metadata
 */
public record ScaffoldAsset(Path path, String content, Kind kind) {
  /** Governed category used to select the required approval. */
  public enum Kind {
    /** Ordinary project content. */
    PROJECT,
    /** Dependency or build metadata requiring dependency approval. */
    DEPENDENCY
  }

  /** Validates and normalizes the confined relative path and required content. */
  public ScaffoldAsset {
    if (path == null
        || path.isAbsolute()
        || path.normalize().startsWith("..")
        || path.getNameCount() == 0
        || content == null
        || kind == null) {
      throw new IllegalArgumentException(
          "a confined relative path, content, and kind are required");
    }
    path = path.normalize();
  }
}

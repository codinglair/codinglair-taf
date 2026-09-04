package com.codinglair.taf.mcp.tools;

import java.util.List;

/**
 * Versioned scaffold content registered by server composition, never by an MCP request.
 *
 * @param name stable template name
 * @param version stable template version
 * @param assets immutable, non-empty assets with unique paths
 */
public record ScaffoldTemplate(String name, String version, List<ScaffoldAsset> assets) {
  /** Validates the template identity and creates an immutable asset list. */
  public ScaffoldTemplate {
    if (!token(name) || !token(version) || assets == null || assets.isEmpty()) {
      throw new IllegalArgumentException("template name, version, and assets are required");
    }
    assets = List.copyOf(assets);
    if (assets.stream().map(ScaffoldAsset::path).distinct().count() != assets.size()) {
      throw new IllegalArgumentException("template asset paths must be unique");
    }
  }

  private static boolean token(String value) {
    return value != null && value.matches("[A-Za-z0-9._-]{1,128}");
  }
}

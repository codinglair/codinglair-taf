package com.codinglair.taf.mcp;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Filesystem boundaries owned by the production MCP application composition. */
@Validated
@ConfigurationProperties("taf.mcp.application")
public final class McpProductionProperties {
  private boolean enabled = true;
  private Path workspaceRoot = Path.of(".");
  private Path stateRoot = Path.of("target", "taf-mcp-state");

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Path getWorkspaceRoot() {
    return workspaceRoot;
  }

  public void setWorkspaceRoot(Path workspaceRoot) {
    this.workspaceRoot = required(workspaceRoot, "workspaceRoot");
  }

  public Path getStateRoot() {
    return stateRoot;
  }

  public void setStateRoot(Path stateRoot) {
    this.stateRoot = required(stateRoot, "stateRoot");
  }

  private static Path required(Path value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value.toAbsolutePath().normalize();
  }
}

package com.codinglair.taf.mcp.stdio;

import com.codinglair.taf.mcp.security.CallerIdentity;
import com.codinglair.taf.mcp.security.IdentityKind;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Local identity and workspace boundary for the STDIO process. */
@Validated
@ConfigurationProperties("taf.mcp.stdio")
public final class TafMcpStdioProperties {
  private boolean enabled = true;
  private Path workspaceRoot = Path.of(".");
  private String userId = "local-user";
  private String agentId = "local-stdio-client";
  private Set<String> roles = new LinkedHashSet<>(Set.of("mcp-local"));
  private String project = "local";
  private String environment = "local";

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
    this.workspaceRoot = require(workspaceRoot, "workspaceRoot");
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = text(userId, "userId");
  }

  public String getAgentId() {
    return agentId;
  }

  public void setAgentId(String agentId) {
    this.agentId = text(agentId, "agentId");
  }

  public Set<String> getRoles() {
    return Set.copyOf(roles);
  }

  public void setRoles(Set<String> roles) {
    if (roles == null
        || roles.isEmpty()
        || roles.stream().anyMatch(role -> role == null || role.isBlank())) {
      throw new IllegalArgumentException("roles must contain non-blank values");
    }
    this.roles = new LinkedHashSet<>(roles);
  }

  public String getProject() {
    return project;
  }

  public void setProject(String project) {
    this.project = text(project, "project");
  }

  public String getEnvironment() {
    return environment;
  }

  public void setEnvironment(String environment) {
    this.environment = text(environment, "environment");
  }

  CallerIdentity identity() {
    return new CallerIdentity(userId, roles, agentId, IdentityKind.AGENT);
  }

  private static String text(String value, String name) {
    if (value == null || value.isBlank() || value.length() > 128) {
      throw new IllegalArgumentException(name + " must be non-blank and at most 128 characters");
    }
    return value;
  }

  private static <T> T require(T value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }
}

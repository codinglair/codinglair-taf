package com.codinglair.taf.mcp.tools;

public enum ToolOperation {
  VALIDATE("taf:project:read", false, 120),
  COMPILE("taf:worker:execute", true, 3600),
  BUILD("taf:worker:execute", true, 7200),
  EXECUTE("taf:test:execute", true, 86400),
  CANCEL("taf:job:cancel", false, 120);

  private final String permission;
  private final boolean asynchronous;
  private final long maximumTimeoutSeconds;

  ToolOperation(String permission, boolean asynchronous, long maximumTimeoutSeconds) {
    this.permission = permission;
    this.asynchronous = asynchronous;
    this.maximumTimeoutSeconds = maximumTimeoutSeconds;
  }

  public String permission() {
    return permission;
  }

  public boolean asynchronous() {
    return asynchronous;
  }

  public long maximumTimeoutSeconds() {
    return maximumTimeoutSeconds;
  }

  public String action() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }
}

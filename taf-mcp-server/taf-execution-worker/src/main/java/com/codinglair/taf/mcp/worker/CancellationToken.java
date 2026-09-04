package com.codinglair.taf.mcp.worker;

@FunctionalInterface
public interface CancellationToken {
  CancellationToken NEVER = () -> false;

  boolean cancellationRequested();
}

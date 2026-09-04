package com.codinglair.taf.mcp.resources;

/** Deliberately generic denial that does not reveal whether a controlled resource exists. */
public final class ResourceAccessDeniedException extends RuntimeException {
  public ResourceAccessDeniedException() {
    super("resource is unavailable");
  }
}

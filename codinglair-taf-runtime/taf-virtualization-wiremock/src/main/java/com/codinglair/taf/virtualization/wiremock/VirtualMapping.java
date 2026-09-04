package com.codinglair.taf.virtualization.wiremock;

import java.util.Map;
import java.util.Objects;

/** Immutable mapping input. Paths are automatically placed in the owning session namespace. */
public record VirtualMapping(
    String method,
    String path,
    int status,
    Map<String, String> headers,
    String body,
    FaultProfile faultProfile) {
  public VirtualMapping {
    method = requireText(method, "method").toUpperCase(java.util.Locale.ROOT);
    path = requireText(path, "path");
    if (!path.startsWith("/") || path.contains(".."))
      throw new IllegalArgumentException("path must be absolute and must not contain '..'");
    if (status < 100 || status > 599)
      throw new IllegalArgumentException("status must be between 100 and 599");
    headers = Map.copyOf(Objects.requireNonNull(headers, "headers"));
    body = Objects.requireNonNull(body, "body");
    faultProfile = Objects.requireNonNull(faultProfile, "faultProfile");
  }

  public VirtualMapping(String method, String path, int status, String body) {
    this(method, path, status, Map.of(), body, FaultProfile.none());
  }

  private static String requireText(String value, String name) {
    String result = Objects.requireNonNull(value, name).trim();
    if (result.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
    return result;
  }
}

package com.codinglair.taf.runtime.core.controller;

import java.util.Map;

/** Vendor-neutral access to resources already provisioned for a session. */
@FunctionalInterface
public interface EnvironmentAccess {
  Resource resource(String name);

  record Resource(String id, String type, Map<String, String> properties) {
    public Resource {
      properties = Map.copyOf(properties);
    }
  }

  static EnvironmentAccess unavailable() {
    return name -> {
      throw new IllegalStateException("No environment resource is available: " + name);
    };
  }
}

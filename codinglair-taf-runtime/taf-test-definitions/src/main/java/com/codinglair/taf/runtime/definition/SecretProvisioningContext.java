package com.codinglair.taf.runtime.definition;

import java.util.Objects;

/** Non-sensitive authorization and correlation metadata for protection. */
public record SecretProvisioningContext(
    String project,
    String environment,
    String source,
    String definitionId,
    String field,
    String caller,
    boolean authorized) {
  public SecretProvisioningContext {
    project = require(project, "project");
    environment = require(environment, "environment");
    source = require(source, "source");
    definitionId = require(definitionId, "definitionId");
    field = require(field, "field");
    caller = require(caller, "caller");
  }

  private static String require(String value, String name) {
    value = Objects.requireNonNull(value, name).trim();
    if (value.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
    return value;
  }
}

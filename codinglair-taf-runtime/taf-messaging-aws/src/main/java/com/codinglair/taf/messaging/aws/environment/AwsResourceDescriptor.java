package com.codinglair.taf.messaging.aws.environment;

import java.util.Objects;

/** Safe, immutable description of an AWS resource made available by an environment provider. */
public record AwsResourceDescriptor(String type, String logicalName, String physicalId) {
  public AwsResourceDescriptor {
    type = required(type, "type");
    logicalName = required(logicalName, "logicalName");
    physicalId = required(physicalId, "physicalId");
  }

  private static String required(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    return value;
  }
}

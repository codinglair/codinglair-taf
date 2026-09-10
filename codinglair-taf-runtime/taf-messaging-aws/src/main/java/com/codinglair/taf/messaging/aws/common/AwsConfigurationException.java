package com.codinglair.taf.messaging.aws.common;

import java.util.Objects;

/** Structured, sanitized failure raised while validating the {@code taf.aws} boundary. */
public final class AwsConfigurationException extends IllegalArgumentException {
  private final String path;
  private final String correctiveAction;

  AwsConfigurationException(String path, String reason) {
    super("Invalid AWS configuration at " + path + ": " + reason);
    this.path = Objects.requireNonNull(path, "path");
    correctiveAction = Objects.requireNonNull(reason, "reason");
  }

  public String capability() {
    return "AWS messaging";
  }

  public String path() {
    return path;
  }

  public String correctiveAction() {
    return correctiveAction;
  }
}

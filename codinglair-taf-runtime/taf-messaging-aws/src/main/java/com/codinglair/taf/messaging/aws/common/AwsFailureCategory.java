package com.codinglair.taf.messaging.aws.common;

/** Stable failure categories; SDK exception types never cross the framework contract. */
public enum AwsFailureCategory {
  CONFIGURATION,
  AUTHENTICATION,
  AUTHORIZATION,
  THROTTLING,
  TRANSIENT_SERVICE,
  TIMEOUT,
  MISSING_RESOURCE,
  INVALID_REQUEST,
  OWNERSHIP_VIOLATION,
  UNSUPPORTED_EMULATOR_BEHAVIOR,
  AUTOMATION
}

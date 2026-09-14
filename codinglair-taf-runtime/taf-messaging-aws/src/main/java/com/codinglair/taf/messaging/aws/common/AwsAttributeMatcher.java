package com.codinglair.taf.messaging.aws.common;

import java.util.Map;
import java.util.Objects;

/** SDK-independent exact matching for correlation identity and selected attributes. */
public final class AwsAttributeMatcher {
  private AwsAttributeMatcher() {}

  public static boolean matches(
      String expectedCorrelation,
      Map<String, String> expectedAttributes,
      String actualCorrelation,
      Map<String, String> actualAttributes) {
    Objects.requireNonNull(expectedAttributes, "expectedAttributes");
    Objects.requireNonNull(actualAttributes, "actualAttributes");
    return (expectedCorrelation == null || expectedCorrelation.equals(actualCorrelation))
        && expectedAttributes.entrySet().stream()
            .allMatch(
                entry -> Objects.equals(entry.getValue(), actualAttributes.get(entry.getKey())));
  }
}

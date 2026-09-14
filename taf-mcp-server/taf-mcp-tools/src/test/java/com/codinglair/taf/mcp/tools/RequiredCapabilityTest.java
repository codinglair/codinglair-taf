package com.codinglair.taf.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Required capability contract")
class RequiredCapabilityTest {
  @Nested
  @DisplayName("Valid operations")
  class ValidOperations {
    @Test
    @DisplayName("defensively copies safe operation identifiers")
    void copiesOperations() {
      var operations = new HashSet<>(Set.of("publish", "verify-route"));
      var requirement = new RequiredCapability("aws.eventbridge", "orders", operations);
      operations.clear();
      assertThat(requirement.operations()).containsExactlyInAnyOrder("publish", "verify-route");
    }
  }

  @Nested
  @DisplayName("Invalid operations")
  class InvalidOperations {
    @Test
    @DisplayName("rejects null and empty operation sets")
    void rejectsMissingOperations() {
      assertThrows(
          NullPointerException.class, () -> new RequiredCapability("aws.sqs", "orders", null));
      assertThrows(
          IllegalArgumentException.class,
          () -> new RequiredCapability("aws.sqs", "orders", Set.of()));
    }

    @Test
    @DisplayName("rejects unsafe operation names")
    void rejectsInvalidOperationNames() {
      var failure =
          assertThrows(
              IllegalArgumentException.class,
              () -> new RequiredCapability("aws.sqs", "orders", Set.of("delete_queue")));
      assertThat(failure).hasMessage("operations must contain safe operation identifiers");
    }
  }
}

package com.codinglair.taf.mcp.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codinglair.taf.runtime.environment.EnvironmentStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("Capability instance descriptor")
class CapabilityInstanceDescriptorTest {
  @Nested
  @DisplayName("Readiness states")
  class ReadinessStates {
    @ParameterizedTest(name = "preserves {0}")
    @EnumSource(EnvironmentStatus.class)
    @DisplayName("preserves every stable environment readiness state")
    void preservesReadiness(EnvironmentStatus status) {
      assertThat(descriptor("orders", status).readiness()).isEqualTo(status);
    }

    @Test
    @DisplayName("rejects missing readiness")
    void rejectsMissingReadiness() {
      assertThrows(IllegalArgumentException.class, () -> descriptor("orders", null));
    }
  }

  @Nested
  @DisplayName("Safe snapshots")
  class SafeSnapshots {
    @Test
    @DisplayName("serializes multiple instances deterministically without prohibited fields")
    void serializesMultiInstanceSnapshot() throws Exception {
      var instances =
          List.of(
                  descriptor("orders-b", EnvironmentStatus.DEGRADED),
                  descriptor("orders-a", EnvironmentStatus.READY))
              .stream()
              .sorted(Comparator.comparing(CapabilityInstanceDescriptor::instance))
              .toList();

      var snapshot = new ObjectMapper().writeValueAsString(instances);

      assertThat(snapshot)
          .isEqualTo(
              "[{\"capabilityId\":\"aws.sqs\",\"instance\":\"orders-a\","
                  + "\"resourceAlias\":\"queue/orders-a\",\"ownershipMode\":\"TEST_OWNED\","
                  + "\"isolationMode\":\"DEDICATED_RESOURCE\",\"readiness\":\"READY\","
                  + "\"diagnostics\":[]},{\"capabilityId\":\"aws.sqs\",\"instance\":\"orders-b\","
                  + "\"resourceAlias\":\"queue/orders-b\",\"ownershipMode\":\"TEST_OWNED\","
                  + "\"isolationMode\":\"DEDICATED_RESOURCE\",\"readiness\":\"DEGRADED\","
                  + "\"diagnostics\":[]}]")
          .doesNotContain("receiptHandle", "credential", "endpoint", "payload");
    }
  }

  private static CapabilityInstanceDescriptor descriptor(
      String instance, EnvironmentStatus status) {
    return new CapabilityInstanceDescriptor(
        "aws.sqs",
        instance,
        "queue/" + instance,
        "TEST_OWNED",
        "DEDICATED_RESOURCE",
        status,
        List.of());
  }
}

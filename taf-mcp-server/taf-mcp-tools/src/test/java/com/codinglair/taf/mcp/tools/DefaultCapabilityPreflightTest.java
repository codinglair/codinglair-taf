package com.codinglair.taf.mcp.tools;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codinglair.taf.mcp.security.CallerIdentity;
import com.codinglair.taf.mcp.security.IdentityKind;
import com.codinglair.taf.mcp.security.Transport;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AWS capability job preflight")
class DefaultCapabilityPreflightTest {
  private static final ConfiguredCapability SQS =
      new ConfiguredCapability(
          "aws.sqs",
          "orders",
          "local",
          true,
          true,
          "TEST_OWNED",
          "DEDICATED_RESOURCE",
          Set.of("send", "await"));

  @Nested
  @DisplayName("Admission")
  class Admission {
    @Test
    @DisplayName("accepts an installed ready compatible named instance and permitted operations")
    void acceptsValidRequirement() {
      var preflight = new DefaultCapabilityPreflight(List.of(SQS));
      assertThatCode(() -> preflight.validate(request("local", "orders", Set.of("await"))))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects missing instances before dispatch")
    void rejectsMissingInstance() {
      var failure =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  new DefaultCapabilityPreflight(List.of(SQS))
                      .validate(request("local", "missing", Set.of("await"))));
      org.assertj.core.api.Assertions.assertThat(failure)
          .hasMessage("required capability is not installed");
    }

    @Test
    @DisplayName("rejects unauthorized environments")
    void rejectsUnauthorizedEnvironment() {
      var preflight = new DefaultCapabilityPreflight(List.of(SQS));
      var failure =
          assertThrows(
              IllegalArgumentException.class,
              () -> preflight.validate(request("production", "orders", Set.of("await"))));
      org.assertj.core.api.Assertions.assertThat(failure)
          .hasMessage("capability environment is not authorized");
    }

    @Test
    @DisplayName("rejects forbidden operations")
    void rejectsForbiddenOperation() {
      var failure =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  new DefaultCapabilityPreflight(List.of(SQS))
                      .validate(request("local", "orders", Set.of("delete-queue"))));
      org.assertj.core.api.Assertions.assertThat(failure)
          .hasMessage("capability operation is not permitted");
    }

    @Test
    @DisplayName("rejects unready environments")
    void rejectsUnreadyEnvironment() {
      var unavailable =
          new ConfiguredCapability(
              "aws.sqs",
              "down",
              "local",
              true,
              false,
              "EXTERNAL",
              "CONTROLLED_CONSUMER",
              Set.of("await"));
      var failure =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  new DefaultCapabilityPreflight(List.of(unavailable))
                      .validate(request("local", "down", Set.of("await"))));
      org.assertj.core.api.Assertions.assertThat(failure)
          .hasMessage("capability environment is not ready");
    }

    @Test
    @DisplayName("rejects external resources that claim dedicated isolation")
    void rejectsIncompatibleOwnership() {
      var unsafe =
          new ConfiguredCapability(
              "aws.sqs",
              "shared",
              "local",
              true,
              true,
              "EXTERNAL",
              "DEDICATED_RESOURCE",
              Set.of("await"));
      var failure =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  new DefaultCapabilityPreflight(List.of(unsafe))
                      .validate(request("local", "shared", Set.of("await"))));
      org.assertj.core.api.Assertions.assertThat(failure)
          .hasMessage("capability ownership and isolation are incompatible");
    }
  }

  private static ToolRequest request(String environment, String instance, Set<String> operations) {
    return new ToolRequest(
        "request-1",
        ToolOperation.EXECUTE,
        "project",
        environment,
        Path.of("."),
        Optional.empty(),
        Duration.ofSeconds(30),
        "idempotency-key-1",
        null,
        null,
        new CallerIdentity("user", Set.of("tester"), "agent", IdentityKind.AGENT),
        Transport.INTERNAL,
        List.of(new RequiredCapability("aws.sqs", instance, operations)));
  }
}

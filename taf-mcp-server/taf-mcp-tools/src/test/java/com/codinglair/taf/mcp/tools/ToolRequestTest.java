package com.codinglair.taf.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;
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

@DisplayName("Tool request capability contract")
class ToolRequestTest {
  @Nested
  @DisplayName("Required capabilities")
  class RequiredCapabilities {
    @Test
    @DisplayName("rejects a null capability collection")
    void rejectsNullCollection() {
      var failure = assertThrows(IllegalArgumentException.class, () -> request(null));
      assertThat(failure).hasMessage("requiredCapabilities is required");
    }

    @Test
    @DisplayName("accepts an empty collection for backward-compatible jobs")
    void acceptsEmptyCollection() {
      assertThat(request(List.of()).requiredCapabilities()).isEmpty();
    }
  }

  private static ToolRequest request(List<RequiredCapability> capabilities) {
    return new ToolRequest(
        "request",
        ToolOperation.EXECUTE,
        "project",
        "local",
        Path.of("."),
        Optional.empty(),
        Duration.ofSeconds(30),
        "idempotency-key-1",
        null,
        null,
        new CallerIdentity("user", Set.of("tester"), "agent", IdentityKind.AGENT),
        Transport.INTERNAL,
        capabilities);
  }
}

package com.codinglair.taf.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Named SUT connection registry")
class SutConnectionRegistryTest {
  @Nested
  @DisplayName("selection")
  class Selection {
    @Test
    @DisplayName("selects multiple same-technology connections deterministically")
    void selectsNamedConnections() {
      SutConnectionRegistry registry =
          new SutConnectionRegistry(
              List.of(
                  descriptor("orders", "jdbc:postgresql://db/orders"),
                  descriptor("customers", "jdbc:postgresql://db/customers")),
              null);
      assertThat(registry.require("orders").jdbcUrl()).endsWith("/orders");
      assertThat(registry.require("customers").jdbcUrl()).endsWith("/customers");
    }

    @Test
    @DisplayName("rejects unknown names")
    void rejectsUnknown() {
      assertThatThrownBy(
              () ->
                  new SutConnectionRegistry(List.of(descriptor("orders", "jdbc:test:orders")), null)
                      .require("audit"))
          .hasMessageContaining("Unknown");
    }
  }

  @Nested
  @DisplayName("preflight validation")
  class Validation {
    @Test
    @DisplayName("rejects duplicate and case-ambiguous names")
    void rejectsDuplicates() {
      assertThatThrownBy(
              () ->
                  new SutConnectionRegistry(
                      List.of(
                          descriptor("orders", "jdbc:test:1"), descriptor("orders", "jdbc:test:2")),
                      null))
          .hasMessageContaining("Duplicate");
    }

    @Test
    @DisplayName("rejects reserved names")
    void rejectsReserved() {
      assertThatThrownBy(() -> descriptor("context", "jdbc:test:1"))
          .hasMessageContaining("Reserved");
    }

    @Test
    @DisplayName("rejects context-plane aliases")
    void rejectsContextAlias() {
      assertThatThrownBy(
              () ->
                  new SutConnectionRegistry(
                      List.of(descriptor("orders", "jdbc:test:context")), " JDBC:TEST:CONTEXT "))
          .hasMessageContaining("must not alias");
    }
  }

  static SutConnectionDescriptor descriptor(String name, String url) {
    return new SutConnectionDescriptor(
        name,
        "postgresql",
        url,
        "user",
        "",
        ConnectionMode.EXTERNAL,
        DatabaseAccess.READ_WRITE,
        Duration.ofSeconds(2),
        CleanupPolicy.NONE,
        true,
        false,
        Set.of("secret_value"),
        "");
  }
}

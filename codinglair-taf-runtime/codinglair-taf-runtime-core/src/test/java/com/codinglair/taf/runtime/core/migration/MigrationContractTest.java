package com.codinglair.taf.runtime.core.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Neutral data migration contracts")
class MigrationContractTest {
  @Nested
  @DisplayName("request validation")
  class RequestValidation {
    @Test
    @DisplayName("requires locations for enabled migration policies")
    void locationsRequired() {
      assertThat(
              assertThrows(
                  IllegalArgumentException.class,
                  () ->
                      new MigrationRequest(
                          "orders",
                          "postgresql",
                          MigrationTarget.TESTCONTAINERS_SUT,
                          MigrationPolicy.VALIDATE_ONLY,
                          List.of())))
          .hasMessageContaining("locations");
    }

    @Test
    @DisplayName("allows a disabled request without migration locations")
    void disabled() {
      assertThat(
              new MigrationRequest(
                      "orders",
                      "postgresql",
                      MigrationTarget.EXTERNAL_SHARED_SUT,
                      MigrationPolicy.DISABLED,
                      List.of())
                  .locations())
          .isEmpty();
    }
  }
}

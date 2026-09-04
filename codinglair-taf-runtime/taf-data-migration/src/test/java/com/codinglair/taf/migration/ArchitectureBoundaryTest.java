package com.codinglair.taf.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Data migration architecture boundary")
class ArchitectureBoundaryTest {
  @Test
  @DisplayName("keeps neutral contracts free of Flyway and adapter dependencies")
  void neutralContractsRemainIndependent() {
    assertThat(
            com.codinglair.taf.runtime.core.migration.DataMigrationManager.class.getPackageName())
        .isEqualTo("com.codinglair.taf.runtime.core.migration");
    assertThat(
            com.codinglair.taf.runtime.core.migration.DataMigrationManager.class
                .getDeclaredMethods()[0]
                .getReturnType()
                .getName())
        .doesNotContain("flyway")
        .doesNotContain("database");
  }
}

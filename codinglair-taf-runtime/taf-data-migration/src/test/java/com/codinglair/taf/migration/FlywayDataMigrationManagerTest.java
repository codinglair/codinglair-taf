package com.codinglair.taf.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.database.CleanupPolicy;
import com.codinglair.taf.database.ConnectionMode;
import com.codinglair.taf.database.DatabaseAccess;
import com.codinglair.taf.database.JdbcConnectionFactory;
import com.codinglair.taf.database.SutConnectionDescriptor;
import com.codinglair.taf.database.SutConnectionRegistry;
import com.codinglair.taf.runtime.core.migration.MigrationOutcome;
import com.codinglair.taf.runtime.core.migration.MigrationPolicy;
import com.codinglair.taf.runtime.core.migration.MigrationRequest;
import com.codinglair.taf.runtime.core.migration.MigrationTarget;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Flyway data migration manager")
class FlywayDataMigrationManagerTest {
  private final SutConnectionDescriptor descriptor =
      new SutConnectionDescriptor(
          "orders",
          "postgresql",
          "jdbc:postgresql://secret-host/orders",
          "",
          "",
          ConnectionMode.TESTCONTAINERS,
          DatabaseAccess.READ_WRITE,
          Duration.ofSeconds(5),
          CleanupPolicy.NONE,
          false,
          false,
          Set.of(),
          "");
  private final JdbcConnectionFactory noConnection =
      (selected, session) -> {
        throw new AssertionError("validation should fail before connection");
      };

  @Nested
  @DisplayName("actionable validation")
  class ActionableValidation {
    @Test
    @DisplayName("rejects a missing location without exposing its physical value")
    void missingLocation() {
      var result = manager().execute(request("postgresql", "classpath:private/missing/location"));
      assertThat(result.outcome()).isEqualTo(MigrationOutcome.FAILED);
      assertThat(result.validation().diagnostics())
          .singleElement()
          .asString()
          .contains("location is missing")
          .doesNotContain("private/missing");
    }

    @Test
    @DisplayName("rejects unsupported database technology before connecting")
    void unsupportedTechnology() {
      var result = manager().execute(request("oracle", "classpath:db/migration/orders"));
      assertThat(result.validation().diagnostics())
          .singleElement()
          .asString()
          .contains("unsupported")
          .doesNotContain("secret-host");
    }
  }

  private FlywayDataMigrationManager manager() {
    return new FlywayDataMigrationManager(
        new SutConnectionRegistry(List.of(descriptor), null), noConnection, request -> true);
  }

  private static MigrationRequest request(String technology, String location) {
    return new MigrationRequest(
        "orders",
        technology,
        MigrationTarget.TESTCONTAINERS_SUT,
        MigrationPolicy.VALIDATE_AND_MIGRATE,
        List.of(location));
  }
}

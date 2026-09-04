package com.codinglair.taf.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.database.CleanupPolicy;
import com.codinglair.taf.database.ConnectionMode;
import com.codinglair.taf.database.DatabaseAccess;
import com.codinglair.taf.database.JdbcConnectionFactory;
import com.codinglair.taf.database.SutConnectionDescriptor;
import com.codinglair.taf.database.SutConnectionRegistry;
import com.codinglair.taf.runtime.core.migration.DataMigrationManager;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflightContributor;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("Data migration Spring Boot auto-configuration")
class DataMigrationAutoConfigurationTest {
  private final SutConnectionRegistry registry =
      new SutConnectionRegistry(List.of(descriptor()), null);
  private final JdbcConnectionFactory factory =
      (descriptor, session) -> {
        throw new AssertionError("must not connect");
      };
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(DataMigrationAutoConfiguration.class))
          .withBean(SutConnectionRegistry.class, () -> registry)
          .withBean(JdbcConnectionFactory.class, () -> factory);

  @Nested
  @DisplayName("conditions and safety defaults")
  class Conditions {
    @Test
    @DisplayName("stays absent unless explicitly enabled")
    void disabled() {
      runner.run(context -> assertThat(context).doesNotHaveBean(DataMigrationManager.class));
    }

    @Test
    @DisplayName("creates the manager and preflight contributor when enabled")
    void enabled() {
      runner
          .withPropertyValues("taf.migration.enabled=true")
          .run(
              context ->
                  assertThat(context)
                      .hasSingleBean(DataMigrationManager.class)
                      .hasSingleBean(MigrationAuthorization.class)
                      .hasSingleBean(ConsumerPreflightContributor.class));
    }

    @Test
    @DisplayName("denies external migration even when YAML requests migration")
    void externalDenied() {
      runner
          .withPropertyValues(
              "taf.migration.enabled=true",
              "taf.migration.connections.orders.policy=validate-and-migrate",
              "taf.migration.connections.orders.locations[0]=classpath:db/migration/orders")
          .run(
              context -> {
                var diagnostics = context.getBean(ConsumerPreflightContributor.class).inspect();
                assertThat(diagnostics)
                    .singleElement()
                    .satisfies(
                        diagnostic -> {
                          assertThat(diagnostic.checkId()).isEqualTo("migration.orders");
                          assertThat(diagnostic.message())
                              .contains("not authorized")
                              .doesNotContain("jdbc:");
                        });
              });
    }

    @Test
    @DisplayName("denies production migration with the default authorization policy")
    void productionDenied() {
      runner
          .withPropertyValues(
              "taf.migration.enabled=true",
              "taf.database.environment=production",
              "taf.migration.connections.orders.policy=validate-only",
              "taf.migration.connections.orders.locations[0]=classpath:db/migration/orders")
          .run(
              context ->
                  assertThat(context.getBean(ConsumerPreflightContributor.class).inspect())
                      .singleElement()
                      .extracting("message")
                      .asString()
                      .contains("not authorized"));
    }
  }

  private static SutConnectionDescriptor descriptor() {
    return new SutConnectionDescriptor(
        "orders",
        "postgresql",
        "jdbc:postgresql://secret-host/orders",
        "",
        "",
        ConnectionMode.EXTERNAL,
        DatabaseAccess.READ_WRITE,
        Duration.ofSeconds(5),
        CleanupPolicy.NONE,
        false,
        false,
        Set.of(),
        "");
  }
}

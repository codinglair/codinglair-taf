package com.codinglair.taf.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.lifecycle.TestSessionConfigurer;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflightContributor;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("Database Spring Boot auto-configuration")
class DatabaseAutoConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(DatabaseAutoConfiguration.class));

  @Nested
  @DisplayName("conditions")
  class Conditions {
    @Test
    @DisplayName("stays absent unless explicitly enabled")
    void disabled() {
      runner.run(context -> assertThat(context).doesNotHaveBean(SutConnectionRegistry.class));
    }

    @Test
    @DisplayName("creates registry configurer and preflight for named connections")
    void enabled() {
      runner
          .withPropertyValues(
              "taf.database.enabled=true",
              "taf.database.connections.orders.jdbc-url=jdbc:postgresql://localhost/orders",
              "taf.database.connections.orders.access=read-write")
          .run(
              context -> {
                assertThat(context)
                    .hasSingleBean(SutConnectionRegistry.class)
                    .hasSingleBean(TestSessionConfigurer.class)
                    .hasSingleBean(ConsumerPreflightContributor.class);
                assertThat(context.getBean(SutConnectionRegistry.class).require("orders").name())
                    .isEqualTo("orders");
              });
    }

    @Test
    @DisplayName("uses the registered connection registry for preflight")
    void registeredRegistry() {
      SutConnectionRegistry supplied =
          new SutConnectionRegistry(
              List.of(
                  new SutConnectionDescriptor(
                      "supplied",
                      "postgresql",
                      "jdbc:postgresql://localhost/supplied",
                      "user",
                      "secret:database/supplied",
                      ConnectionMode.EXTERNAL,
                      DatabaseAccess.READ_ONLY,
                      Duration.ofSeconds(5),
                      CleanupPolicy.NONE,
                      false,
                      false,
                      Set.of(),
                      "")),
              null);

      runner
          .withBean(SutConnectionRegistry.class, () -> supplied)
          .withPropertyValues("taf.database.enabled=true")
          .run(
              context -> {
                assertThat(context.getBean(SutConnectionRegistry.class)).isSameAs(supplied);
                assertThat(context.getBean(ConsumerPreflightContributor.class).inspect())
                    .singleElement()
                    .satisfies(
                        diagnostic -> {
                          assertThat(diagnostic.checkId()).isEqualTo("database.supplied");
                          assertThat(diagnostic.message())
                              .isEqualTo("SecretManager is unavailable");
                        });
              });
    }

    @Test
    @DisplayName("reports context-plane alias without exposing the physical identity")
    void alias() {
      runner
          .withPropertyValues(
              "taf.database.enabled=true",
              "taf.context.jdbc-url=jdbc:postgresql://secret-host/context",
              "taf.database.connections.orders.jdbc-url=jdbc:postgresql://secret-host/context")
          .run(
              context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasMessageNotContaining("secret-host");
              });
    }
  }
}

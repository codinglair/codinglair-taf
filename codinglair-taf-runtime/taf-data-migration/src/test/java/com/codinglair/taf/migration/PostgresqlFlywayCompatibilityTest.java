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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DisplayName("Flyway PostgreSQL 17 compatibility")
@EnabledIfSystemProperty(named = "taf.migration.containers", matches = "true")
class PostgresqlFlywayCompatibilityTest {
  private PostgreSQLContainer postgres;
  @TempDir Path migrationDirectory;

  @BeforeAll
  static void requireDocker() {
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is required for the Flyway PostgreSQL compatibility contract");
  }

  @BeforeEach
  void startContainer() throws Exception {
    postgres = new PostgreSQLContainer("postgres:17-alpine");
    postgres.start();
    try (var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var statement = connection.createStatement()) {
      statement.execute("CREATE SCHEMA orders");
      statement.execute("CREATE SCHEMA customers");
    }
  }

  @AfterEach
  void stopContainer() {
    if (postgres != null) postgres.stop();
  }

  @Test
  @DisplayName("maintains independent locations and histories for two named connections")
  void independentHistories() throws Exception {
    SutConnectionDescriptor orders = descriptor("orders", "orders");
    SutConnectionDescriptor customers = descriptor("customers", "customers");
    SutConnectionRegistry registry = new SutConnectionRegistry(List.of(orders, customers), null);
    JdbcConnectionFactory connections =
        (descriptor, _) ->
            DriverManager.getConnection(
                descriptor.jdbcUrl(), postgres.getUsername(), postgres.getPassword());
    var manager = new FlywayDataMigrationManager(registry, connections, request -> true);

    var orderResult = manager.execute(request("orders", "classpath:db/migration/orders"));
    var customerResult = manager.execute(request("customers", "classpath:db/migration/customers"));

    assertThat(orderResult.outcome()).isEqualTo(MigrationOutcome.MIGRATED);
    assertThat(customerResult.outcome()).isEqualTo(MigrationOutcome.MIGRATED);
    assertThat(orderResult.evidence())
        .singleElement()
        .satisfies(
            evidence -> {
              assertThat(evidence.targetName()).isEqualTo("orders");
              assertThat(evidence.version()).isEqualTo("1");
              assertThat(evidence.checksum()).isNotBlank();
            });
    assertThat(tableExists(orders, "orders_item")).isTrue();
    assertThat(tableExists(customers, "customer_record")).isTrue();
    assertThat(tableExists(orders, "customer_record")).isFalse();
  }

  @Test
  @DisplayName("reports checksum drift as a sanitized validation failure")
  void checksumDrift() throws Exception {
    SutConnectionDescriptor orders = descriptor("orders", "orders");
    var manager = manager(orders);
    Path script = migrationDirectory.resolve("V1__orders.sql");
    Files.writeString(script, "CREATE TABLE checksum_item (id INTEGER PRIMARY KEY);");
    MigrationRequest request = request("orders", "filesystem:" + migrationDirectory);
    assertThat(manager.execute(request).outcome()).isEqualTo(MigrationOutcome.MIGRATED);

    Files.writeString(script, "CREATE TABLE checksum_item (id BIGINT PRIMARY KEY);");
    var drift =
        manager.execute(
            new MigrationRequest(
                "orders",
                "postgresql",
                MigrationTarget.TESTCONTAINERS_SUT,
                MigrationPolicy.VALIDATE_ONLY,
                List.of("filesystem:" + migrationDirectory)));

    assertThat(drift.outcome()).isEqualTo(MigrationOutcome.FAILED);
    assertThat(drift.validation().diagnostics())
        .singleElement()
        .asString()
        .contains("validation failed")
        .doesNotContain(postgres.getJdbcUrl());
  }

  @Test
  @DisplayName("reports a failed migration without exposing the physical target")
  void failedMigration() {
    SutConnectionDescriptor orders = descriptor("orders", "orders");
    var failure = manager(orders).execute(request("orders", "classpath:db/migration/failing"));
    assertThat(failure.outcome()).isEqualTo(MigrationOutcome.FAILED);
    assertThat(failure.validation().diagnostics())
        .singleElement()
        .asString()
        .contains("Migration failed")
        .doesNotContain("jdbc:")
        .doesNotContain(postgres.getHost());
  }

  private FlywayDataMigrationManager manager(SutConnectionDescriptor descriptor) {
    SutConnectionRegistry registry = new SutConnectionRegistry(List.of(descriptor), null);
    JdbcConnectionFactory connections =
        (selected, _) ->
            DriverManager.getConnection(
                selected.jdbcUrl(), postgres.getUsername(), postgres.getPassword());
    return new FlywayDataMigrationManager(registry, connections, request -> true);
  }

  private SutConnectionDescriptor descriptor(String name, String schema) {
    return new SutConnectionDescriptor(
        name,
        "postgresql",
        postgres.getJdbcUrl() + "&currentSchema=" + schema,
        postgres.getUsername(),
        "",
        ConnectionMode.TESTCONTAINERS,
        DatabaseAccess.READ_WRITE,
        Duration.ofSeconds(10),
        CleanupPolicy.NONE,
        false,
        false,
        Set.of(),
        "");
  }

  private static MigrationRequest request(String name, String location) {
    return new MigrationRequest(
        name,
        "postgresql",
        MigrationTarget.TESTCONTAINERS_SUT,
        MigrationPolicy.VALIDATE_AND_MIGRATE,
        List.of(location));
  }

  private boolean tableExists(SutConnectionDescriptor descriptor, String table) throws Exception {
    try (var connection =
            DriverManager.getConnection(
                descriptor.jdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var result =
            connection
                .getMetaData()
                .getTables(null, descriptor.name(), table, new String[] {"TABLE"})) {
      return result.next();
    }
  }
}

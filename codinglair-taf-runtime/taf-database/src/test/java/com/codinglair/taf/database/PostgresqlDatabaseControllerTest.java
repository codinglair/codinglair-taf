package com.codinglair.taf.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.runtime.core.TestSession;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DisplayName("PostgreSQL database controller contract")
@EnabledIfSystemProperty(named = "taf.database.containers", matches = "true")
class PostgresqlDatabaseControllerTest {
  static PostgreSQLContainer postgres;

  @BeforeAll
  static void start() {
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is required for the PostgreSQL container contract");
    postgres = new PostgreSQLContainer("postgres:17-alpine");
    postgres.start();
  }

  @AfterAll
  static void stop() {
    if (postgres != null) postgres.stop();
  }

  @Nested
  @DisplayName("operations")
  class Operations {
    @Test
    @DisplayName("selects two named PostgreSQL controllers deterministically in one session")
    void selectsTwoNamedControllers() {
      try (TestSession session = TestSession.create()) {
        register(session, "orders", "");
        register(session, "customers", "");
        assertThat(
                session
                    .getController(DatabaseController.class, "orders")
                    .query("select 'orders' as identity")
                    .rows()
                    .getFirst())
            .containsEntry("identity", "orders");
        assertThat(
                session
                    .getController(DatabaseController.class, "customers")
                    .query("select 'customers' as identity")
                    .rows()
                    .getFirst())
            .containsEntry("identity", "customers");
      }
    }

    @Test
    @DisplayName("supports query update transaction rollback timeout and sanitization")
    void contract() {
      try (TestSession session = session("orders", "delete from db001_rows")) {
        DatabaseController database = session.getController(DatabaseController.class, "orders");
        database.update(
            "create table if not exists db001_rows(id int primary key, secret_value varchar(40))");
        database.cleanup("delete from db001_rows");
        assertThat(database.setup("insert into db001_rows values (?, ?)", 1, "canary-secret"))
            .isOne();
        QueryResult result =
            database.query("select id, secret_value from db001_rows where id=?", 1);
        assertThat(result.rows().getFirst())
            .containsEntry("id", 1)
            .containsEntry("secret_value", "[REDACTED]");
        assertThatThrownBy(
                () ->
                    database.transaction(
                        tx -> {
                          tx.update("insert into db001_rows values (2, 'rollback')");
                          throw new IllegalStateException("force rollback");
                        }))
            .isInstanceOf(IllegalStateException.class);
        assertThat(database.query("select id from db001_rows where id=2").rows()).isEmpty();
        assertThatThrownBy(() -> database.query("select pg_sleep(2)"))
            .isInstanceOf(DatabaseException.class)
            .hasMessageContaining("orders")
            .hasMessageNotContaining(postgres.getPassword());
        assertThat(session.getArtifactCollector().getArtifacts())
            .allSatisfy(
                artifact ->
                    assertThat(artifact.content())
                        .contains("connection=orders")
                        .doesNotContain(
                            "canary-secret",
                            postgres.getPassword(),
                            postgres.getJdbcUrl(),
                            postgres.getUsername()));
      }
    }

    @Test
    @DisplayName("isolates parallel session controller and connection state")
    void isolatesParallelSessions() throws Exception {
      var executor = Executors.newFixedThreadPool(6);
      try {
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
          int id = i;
          tasks.add(
              () -> {
                try (TestSession session = session("db-" + id, "")) {
                  return session
                      .getController(DatabaseController.class, "db-" + id)
                      .query("select current_database() as db")
                      .rows()
                      .getFirst()
                      .get("db")
                      .toString();
                }
              });
        }
        assertThat(
                executor.invokeAll(tasks).stream()
                    .map(
                        future -> {
                          try {
                            return future.get();
                          } catch (Exception failure) {
                            throw new RuntimeException(failure);
                          }
                        })
                    .distinct())
            .hasSize(1);
      } finally {
        executor.shutdownNow();
      }
    }

    @Test
    @DisplayName("runs authorized cleanup when a test fails")
    void cleanupAfterFailure() {
      try {
        try (TestSession session = session("cleanup", "delete from db001_cleanup")) {
          DatabaseController database = session.getController(DatabaseController.class, "cleanup");
          database.update("create table if not exists db001_cleanup(id int)");
          database.update("insert into db001_cleanup values (1)");
          throw new AssertionError("simulated test failure");
        }
      } catch (AssertionError ignored) {
      }
      try (var connection =
              DriverManager.getConnection(
                  postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
          var statement = connection.createStatement();
          var result = statement.executeQuery("select count(*) from db001_cleanup")) {
        result.next();
        assertThat(result.getInt(1)).isZero();
      } catch (Exception failure) {
        throw new RuntimeException(failure);
      }
    }
  }

  private static TestSession session(String name, String cleanupSql) {
    TestSession session = TestSession.create();
    register(session, name, cleanupSql);
    return session;
  }

  private static void register(TestSession session, String name, String cleanupSql) {
    SutConnectionDescriptor descriptor =
        new SutConnectionDescriptor(
            name,
            "postgresql",
            postgres.getJdbcUrl(),
            postgres.getUsername(),
            "",
            ConnectionMode.TESTCONTAINERS,
            DatabaseAccess.READ_WRITE,
            Duration.ofSeconds(1),
            cleanupSql.isBlank() ? CleanupPolicy.NONE : CleanupPolicy.AUTHORIZED_SQL,
            true,
            !cleanupSql.isBlank(),
            Set.of("secret_value"),
            cleanupSql);
    session
        .getControllerRegistry()
        .register(
            DatabaseController.class,
            name,
            new DefaultDatabaseController(
                descriptor,
                (ignored, sessionId) ->
                    DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())));
  }
}

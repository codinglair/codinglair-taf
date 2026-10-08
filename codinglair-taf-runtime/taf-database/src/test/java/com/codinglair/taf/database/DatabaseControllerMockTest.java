package com.codinglair.taf.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.codinglair.taf.runtime.core.TestSession;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("JDBC database controller mock boundary")
class DatabaseControllerMockTest {
  @Nested
  @DisplayName("authorization")
  class Authorization {
    @Test
    @DisplayName("rejects read-only writes before opening a connection")
    void rejectsBeforeExecution() {
      AtomicInteger opens = new AtomicInteger();
      DefaultDatabaseController controller =
          controller(
              DatabaseAccess.READ_ONLY,
              (descriptor, session) -> {
                opens.incrementAndGet();
                return validConnection();
              });
      try (TestSession testSession = TestSession.create()) {
        testSession
            .getControllerRegistry()
            .register(DatabaseController.class, "orders", controller);
        DatabaseController selected = testSession.getController(DatabaseController.class, "orders");
        assertThatThrownBy(() -> selected.update("delete from orders"))
            .isInstanceOf(DatabaseException.class)
            .hasMessageNotContaining("delete from orders");
      }
      assertThat(opens).hasValue(1);
    }
  }

  @Nested
  @DisplayName("lifecycle")
  class Lifecycle {
    @Test
    @DisplayName("closes caller-owned native escape-hatch connections idempotently")
    void closesNativeConnections() throws Exception {
      AtomicBoolean closed = new AtomicBoolean();
      DefaultDatabaseController controller =
          controller(DatabaseAccess.READ_WRITE, (descriptor, session) -> validConnection(closed));
      TestSession session = TestSession.create();
      session.getControllerRegistry().register(DatabaseController.class, "orders", controller);
      Connection nativeConnection =
          session.getController(DatabaseController.class, "orders").nativeConnection();
      session.close();
      session.close();
      assertThat(closed).isTrue();
      assertThat(nativeConnection.isClosed()).isTrue();
    }

    @Test
    @DisplayName("attempts every native connection and suppresses later close failures")
    void aggregatesNativeConnectionFailures() {
      AtomicInteger opens = new AtomicInteger();
      AtomicInteger closeAttempts = new AtomicInteger();
      DefaultDatabaseController controller =
          controller(
              DatabaseAccess.READ_WRITE,
              (descriptor, session) ->
                  opens.getAndIncrement() == 0
                      ? validConnection()
                      : failingCloseConnection(closeAttempts));
      TestSession session = TestSession.create();
      session.getControllerRegistry().register(DatabaseController.class, "orders", controller);
      DatabaseController selected = session.getController(DatabaseController.class, "orders");
      selected.nativeConnection();
      selected.nativeConnection();

      DatabaseException failure = catchThrowableOfType(DatabaseException.class, controller::close);

      assertThat(closeAttempts).hasValue(2);
      assertThat(failure.getCause()).isInstanceOf(SQLException.class);
      assertThat(failure.getCause().getSuppressed()).hasSize(1);
      controller.close();
    }
  }

  @Nested
  @DisplayName("transactions")
  class Transactions {
    @Test
    @DisplayName("preserves the work failure and suppresses a rollback failure")
    void suppressesRollbackFailure() {
      AtomicInteger opens = new AtomicInteger();
      DefaultDatabaseController controller =
          controller(
              DatabaseAccess.READ_WRITE,
              (descriptor, session) ->
                  opens.getAndIncrement() == 0 ? validConnection() : failingRollbackConnection());
      try (TestSession session = TestSession.create()) {
        session.getControllerRegistry().register(DatabaseController.class, "orders", controller);
        DatabaseController selected = session.getController(DatabaseController.class, "orders");

        IllegalStateException failure =
            catchThrowableOfType(
                IllegalStateException.class,
                () ->
                    selected.transaction(
                        transaction -> {
                          throw new IllegalStateException("work failed");
                        }));

        assertThat(failure).hasMessage("work failed");
        assertThat(failure.getSuppressed())
            .singleElement()
            .satisfies(
                suppressed ->
                    assertThat(suppressed)
                        .isInstanceOf(SQLException.class)
                        .hasMessage("rollback failed"));
      }
    }
  }

  private static DefaultDatabaseController controller(
      DatabaseAccess access, JdbcConnectionFactory factory) {
    return new DefaultDatabaseController(
        new SutConnectionDescriptor(
            "orders",
            "postgresql",
            "jdbc:masked",
            "user",
            "",
            ConnectionMode.EXTERNAL,
            access,
            Duration.ofSeconds(1),
            CleanupPolicy.NONE,
            false,
            false,
            Set.of(),
            ""),
        factory);
  }

  private static Connection validConnection() {
    return validConnection(new AtomicBoolean());
  }

  private static Connection validConnection(AtomicBoolean closed) {
    return (Connection)
        Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "isValid" -> true;
                  case "close" -> {
                    closed.set(true);
                    yield null;
                  }
                  case "isClosed" -> closed.get();
                  case "setReadOnly" -> null;
                  case "toString" -> "FakeConnection[REDACTED]";
                  default -> defaultValue(method.getReturnType());
                });
  }

  private static Connection failingCloseConnection(AtomicInteger closeAttempts) {
    return connection(
        (method, args) -> {
          if (method.equals("close")) {
            closeAttempts.incrementAndGet();
            throw new SQLException("close failed");
          }
          return null;
        });
  }

  private static Connection failingRollbackConnection() {
    return connection(
        (method, args) -> {
          if (method.equals("rollback")) throw new SQLException("rollback failed");
          return null;
        });
  }

  private static Connection connection(ConnectionCall call) {
    return (Connection)
        Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
              Object result = call.invoke(method.getName(), args);
              return result == null ? defaultValue(method.getReturnType()) : result;
            });
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) return null;
    if (type == boolean.class) return false;
    if (type == int.class) return 0;
    if (type == long.class) return 0L;
    if (type == double.class) return 0D;
    if (type == float.class) return 0F;
    if (type == short.class) return (short) 0;
    if (type == byte.class) return (byte) 0;
    if (type == char.class) return '\0';
    return null;
  }

  @FunctionalInterface
  private interface ConnectionCall {
    Object invoke(String method, Object[] arguments) throws SQLException;
  }
}

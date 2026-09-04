package com.codinglair.taf.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.runtime.core.TestSession;
import java.lang.reflect.Proxy;
import java.sql.Connection;
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
}

package com.codinglair.taf.database;

import com.codinglair.taf.runtime.core.controller.*;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

public final class DefaultDatabaseController implements DatabaseController {
  private final SutConnectionDescriptor descriptor;
  private final JdbcConnectionFactory connections;
  private final AtomicReference<ControllerState> state = new AtomicReference<>(ControllerState.NEW);
  private final Set<Connection> nativeConnections = ConcurrentHashMap.newKeySet();
  private volatile ControllerContext context;

  public DefaultDatabaseController(
      SutConnectionDescriptor descriptor, JdbcConnectionFactory connections) {
    this.descriptor = Objects.requireNonNull(descriptor);
    this.connections = Objects.requireNonNull(connections);
  }

  @Override
  public ControllerIdentity identity() {
    return new ControllerIdentity(DatabaseController.class, descriptor.name());
  }

  @Override
  public ControllerState state() {
    return state.get();
  }

  @Override
  public void initialize(ControllerContext value) {
    if (!state.compareAndSet(ControllerState.NEW, ControllerState.INITIALIZING))
      throw new IllegalStateException("Controller cannot initialize from " + state.get());
    context = Objects.requireNonNull(value);
    try (Connection connection = open()) {
      connection.setReadOnly(descriptor.access() == DatabaseAccess.READ_ONLY);
      if (!connection.isValid(timeoutSeconds(descriptor.timeout())))
        throw new SQLException("Readiness probe did not succeed");
      state.set(ControllerState.READY);
    } catch (SQLException failure) {
      state.set(ControllerState.FAILED);
      throw failure("readiness", failure);
    }
  }

  @Override
  public HealthResult health() {
    return state.get() == ControllerState.READY
        ? new HealthResult(
            HealthResult.Status.HEALTHY,
            "SUT database controller is ready",
            Map.of(
                "connection",
                descriptor.name(),
                "technology",
                descriptor.technology(),
                "mode",
                descriptor.mode().name(),
                "access",
                descriptor.access().name()))
        : HealthResult.unknown("SUT database controller is not ready: " + state.get());
  }

  @Override
  public Stream<TestArtifact> collectArtifacts(ArtifactReason reason) {
    return Stream.empty();
  }

  @Override
  public QueryResult query(String sql, Object... parameters) {
    requireReady();
    requireSql(sql);
    requireReadStatement(sql);
    try (Connection connection = open();
        PreparedStatement statement = prepare(connection, sql, parameters);
        ResultSet result = statement.executeQuery()) {
      QueryResult rows = read(result);
      evidence("query", rows.rowCount());
      return rows;
    } catch (SQLException failure) {
      throw failure("query", failure);
    }
  }

  @Override
  public int update(String sql, Object... parameters) {
    return write("update", true, sql, parameters);
  }

  @Override
  public int setup(String sql, Object... parameters) {
    if (!descriptor.setupAuthorized()) throw denied("setup");
    return write("setup", true, sql, parameters);
  }

  @Override
  public int cleanup(String sql, Object... parameters) {
    if (!descriptor.cleanupAuthorized()) throw denied("cleanup");
    return write("cleanup", true, sql, parameters);
  }

  @Override
  public <T> T transaction(Function<JdbcTransaction, T> work) {
    requireReady();
    Objects.requireNonNull(work);
    try (Connection connection = open()) {
      connection.setAutoCommit(false);
      try {
        T result = work.apply(new Transaction(connection));
        connection.commit();
        evidence("transaction", 1);
        return result;
      } catch (Throwable failure) {
        try {
          connection.rollback();
        } catch (SQLException rollback) {
          failure.addSuppressed(rollback);
        }
        if (failure instanceof RuntimeException runtime) throw runtime;
        throw new DatabaseException(
            descriptor.name(), "transaction", "verify SQL and transaction data", failure);
      }
    } catch (SQLException failure) {
      throw failure("transaction", failure);
    }
  }

  @Override
  public Connection nativeConnection() {
    requireReady();
    try {
      Connection connection = open();
      nativeConnections.add(connection);
      return connection;
    } catch (SQLException failure) {
      throw failure("native connection", failure);
    }
  }

  @Override
  public void close() {
    if (state.getAndSet(ControllerState.CLOSED) == ControllerState.CLOSED) return;
    Throwable failure = null;
    if (!descriptor.cleanupSql().isBlank() && descriptor.cleanupAuthorized()) {
      try {
        writeClosedCleanup(descriptor.cleanupSql());
      } catch (Throwable current) {
        failure = current;
      }
    }
    for (Connection connection : nativeConnections)
      try {
        connection.close();
      } catch (SQLException current) {
        if (failure == null) failure = current;
        else failure.addSuppressed(current);
      }
    nativeConnections.clear();
    if (failure != null)
      throw new DatabaseException(
          descriptor.name(), "close", "verify authorized cleanup SQL and connectivity", failure);
  }

  private int write(String operation, boolean requireWrite, String sql, Object... parameters) {
    requireReady();
    requireSql(sql);
    if (requireWrite && descriptor.access() != DatabaseAccess.READ_WRITE) throw denied(operation);
    try (Connection connection = open();
        PreparedStatement statement = prepare(connection, sql, parameters)) {
      int count = statement.executeUpdate();
      evidence(operation, count);
      return count;
    } catch (SQLException failure) {
      throw failure(operation, failure);
    }
  }

  private void writeClosedCleanup(String sql) throws SQLException {
    try (Connection connection = open();
        PreparedStatement statement = prepare(connection, sql, new Object[0])) {
      statement.executeUpdate();
    }
  }

  private Connection open() throws SQLException {
    Connection connection = connections.open(descriptor, context.sessionId());
    connection.setReadOnly(descriptor.access() == DatabaseAccess.READ_ONLY);
    return connection;
  }

  private PreparedStatement prepare(Connection connection, String sql, Object[] parameters)
      throws SQLException {
    PreparedStatement statement = connection.prepareStatement(sql);
    statement.setQueryTimeout(timeoutSeconds(descriptor.timeout()));
    for (int index = 0; index < parameters.length; index++)
      statement.setObject(index + 1, parameters[index]);
    return statement;
  }

  private QueryResult read(ResultSet result) throws SQLException {
    List<Map<String, Object>> rows = new ArrayList<>();
    ResultSetMetaData metadata = result.getMetaData();
    while (result.next()) {
      Map<String, Object> row = new LinkedHashMap<>();
      for (int index = 1; index <= metadata.getColumnCount(); index++) {
        String label = metadata.getColumnLabel(index);
        row.put(
            label,
            descriptor.sensitiveColumns().contains(label.toLowerCase(Locale.ROOT))
                ? "[REDACTED]"
                : result.getObject(index));
      }
      rows.add(row);
    }
    return new QueryResult(rows);
  }

  private void evidence(String operation, int affected) {
    context
        .artifacts()
        .addArtifact(
            "database-" + context.artifacts().nextSequenceNumber() + ".txt",
            "database-operation",
            "connection="
                + descriptor.name()
                + "\ntechnology="
                + descriptor.technology()
                + "\noperation="
                + operation
                + "\naffected="
                + affected,
            "text/plain",
            null);
  }

  private void requireReady() {
    if (state.get() != ControllerState.READY)
      throw new IllegalStateException("Database controller is not READY: " + state.get());
  }

  private static void requireSql(String sql) {
    if (sql == null || sql.isBlank()) throw new IllegalArgumentException("SQL must not be blank");
  }

  private static void requireReadStatement(String sql) {
    String token = sql.stripLeading().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
    if (!Set.of("select", "with", "show", "explain", "values").contains(token))
      throw new IllegalArgumentException("Query operation accepts read statements only");
  }

  private static int timeoutSeconds(Duration timeout) {
    return Math.max(1, (int) Math.ceil(timeout.toMillis() / 1000.0));
  }

  private DatabaseException denied(String operation) {
    return new DatabaseException(
        descriptor.name(),
        operation,
        "request explicit read/write authorization for this SUT connection",
        new SecurityException("Operation is not authorized"));
  }

  private DatabaseException failure(String operation, SQLException cause) {
    SQLException sanitized =
        new SQLException("JDBC operation failed", cause.getSQLState(), cause.getErrorCode());
    return new DatabaseException(
        descriptor.name(),
        operation,
        "verify readiness, SQL, timeout, and connection authorization",
        sanitized);
  }

  private final class Transaction implements JdbcTransaction {
    private final Connection connection;

    private Transaction(Connection connection) {
      this.connection = connection;
    }

    @Override
    public QueryResult query(String sql, Object... parameters) {
      requireSql(sql);
      requireReadStatement(sql);
      try (PreparedStatement statement = prepare(connection, sql, parameters);
          ResultSet result = statement.executeQuery()) {
        return read(result);
      } catch (SQLException failure) {
        throw failure("transaction query", failure);
      }
    }

    @Override
    public int update(String sql, Object... parameters) {
      if (descriptor.access() != DatabaseAccess.READ_WRITE) throw denied("transaction update");
      requireSql(sql);
      try (PreparedStatement statement = prepare(connection, sql, parameters)) {
        return statement.executeUpdate();
      } catch (SQLException failure) {
        throw failure("transaction update", failure);
      }
    }

    @Override
    public Connection nativeConnection() {
      return connection;
    }
  }
}

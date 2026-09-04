package com.codinglair.taf.migration;

import com.codinglair.taf.database.JdbcConnectionFactory;
import com.codinglair.taf.database.SutConnectionDescriptor;
import com.codinglair.taf.database.SutConnectionRegistry;
import com.codinglair.taf.runtime.core.migration.DataMigrationManager;
import com.codinglair.taf.runtime.core.migration.MigrationEvidence;
import com.codinglair.taf.runtime.core.migration.MigrationOutcome;
import com.codinglair.taf.runtime.core.migration.MigrationPolicy;
import com.codinglair.taf.runtime.core.migration.MigrationRequest;
import com.codinglair.taf.runtime.core.migration.MigrationResult;
import com.codinglair.taf.runtime.core.migration.MigrationValidationResult;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;

final class FlywayDataMigrationManager implements DataMigrationManager {
  private final SutConnectionRegistry registry;
  private final JdbcConnectionFactory connections;
  private final MigrationAuthorization authorization;

  FlywayDataMigrationManager(
      SutConnectionRegistry registry,
      JdbcConnectionFactory connections,
      MigrationAuthorization authorization) {
    this.registry = registry;
    this.connections = connections;
    this.authorization = authorization;
  }

  @Override
  public MigrationResult execute(MigrationRequest request) {
    if (request.policy() == MigrationPolicy.DISABLED)
      return new MigrationResult(
          MigrationOutcome.DISABLED, MigrationValidationResult.passed(), List.of());
    if (!authorization.permits(request))
      return failed("Migration is not authorized for this target");
    if (!request.technology().equalsIgnoreCase("postgresql"))
      return failed("Database technology or version is unsupported");
    if (request.locations().stream().anyMatch(location -> !locationExists(location)))
      return failed("A configured migration location is missing");

    SutConnectionDescriptor descriptor;
    try {
      descriptor = registry.require(request.targetName());
    } catch (RuntimeException _) {
      return failed("The named migration target is unavailable");
    }
    try {
      Flyway flyway =
          Flyway.configure()
              .dataSource(new FactoryDataSource(connections, descriptor))
              .locations(request.locations().toArray(String[]::new))
              .baselineOnMigrate(false)
              .cleanDisabled(true)
              .load();
      if (request.policy() == MigrationPolicy.VALIDATE_ONLY) {
        var validation = flyway.validateWithResult();
        if (!validation.validationSuccessful) return failed("Migration validation failed");
        return new MigrationResult(
            MigrationOutcome.VALIDATED,
            MigrationValidationResult.passed(),
            evidence(request.targetName(), flyway.info().applied(), MigrationOutcome.VALIDATED));
      }
      // migrate validates the applied history before executing pending scripts. A separate
      // validate call would reject a legitimate empty history because its scripts are pending.
      flyway.migrate();
      return new MigrationResult(
          MigrationOutcome.MIGRATED,
          MigrationValidationResult.passed(),
          evidence(request.targetName(), flyway.info().applied(), MigrationOutcome.MIGRATED));
    } catch (RuntimeException _) {
      return failed("Migration failed; inspect the logical target history and Git migrations");
    }
  }

  private static List<MigrationEvidence> evidence(
      String target, MigrationInfo[] migrations, MigrationOutcome outcome) {
    return java.util.Arrays.stream(migrations)
        .map(
            info ->
                new MigrationEvidence(
                    target,
                    info.getVersion() == null ? "repeatable" : info.getVersion().getVersion(),
                    info.getChecksum() == null
                        ? "unavailable"
                        : Integer.toUnsignedString(info.getChecksum()),
                    outcome,
                    Duration.ofMillis(
                        info.getExecutionTime() == null
                            ? 0
                            : Math.max(0, info.getExecutionTime()))))
        .toList();
  }

  private static MigrationResult failed(String diagnostic) {
    return new MigrationResult(
        MigrationOutcome.FAILED, MigrationValidationResult.failed(diagnostic), List.of());
  }

  private static boolean locationExists(String location) {
    if (location.startsWith("classpath:")) {
      String resource = location.substring("classpath:".length()).replaceFirst("^/+", "");
      return Thread.currentThread().getContextClassLoader().getResource(resource) != null;
    }
    if (location.startsWith("filesystem:")) return Files.exists(Path.of(location.substring(11)));
    return false;
  }

  private static final class FactoryDataSource implements DataSource {
    private final JdbcConnectionFactory factory;
    private final SutConnectionDescriptor descriptor;

    private FactoryDataSource(JdbcConnectionFactory factory, SutConnectionDescriptor descriptor) {
      this.factory = factory;
      this.descriptor = descriptor;
    }

    @Override
    public Connection getConnection() throws SQLException {
      return factory.open(descriptor, "migration-" + descriptor.name());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
      return getConnection();
    }

    @Override
    public PrintWriter getLogWriter() {
      return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {}

    @Override
    public void setLoginTimeout(int seconds) {}

    @Override
    public int getLoginTimeout() {
      return 0;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
      throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
      if (iface.isInstance(this)) return iface.cast(this);
      throw new SQLException("Unsupported unwrap operation");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
      return iface.isInstance(this);
    }
  }
}

package com.codinglair.taf.database;

import com.codinglair.taf.runtime.secret.ResolvedSecret;
import com.codinglair.taf.runtime.secret.SecretManager;
import com.codinglair.taf.runtime.secret.SecretRequestContext;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

public final class DefaultJdbcConnectionFactory implements JdbcConnectionFactory {
  private final SecretManager secrets;
  private final String environment;

  public DefaultJdbcConnectionFactory(SecretManager secrets, String environment) {
    this.secrets = secrets;
    this.environment = Objects.requireNonNull(environment);
  }

  @Override
  public Connection open(SutConnectionDescriptor descriptor, String sessionId) throws SQLException {
    if (descriptor.passwordReference().isBlank())
      return DriverManager.getConnection(descriptor.jdbcUrl(), descriptor.username(), "");
    if (secrets == null)
      throw new SQLException("A SecretManager is required for the configured credential reference");
    try (ResolvedSecret password =
        secrets.resolve(
            descriptor.passwordReference(),
            new SecretRequestContext("taf-database", sessionId, environment, true))) {
      return DriverManager.getConnection(
          descriptor.jdbcUrl(), descriptor.username(), password.useAsString());
    }
  }
}

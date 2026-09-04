package com.codinglair.taf.database;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record SutConnectionDescriptor(
    String name,
    String technology,
    String jdbcUrl,
    String username,
    String passwordReference,
    ConnectionMode mode,
    DatabaseAccess access,
    Duration timeout,
    CleanupPolicy cleanupPolicy,
    boolean setupAuthorized,
    boolean cleanupAuthorized,
    Set<String> sensitiveColumns,
    String cleanupSql) {
  public static final Set<String> RESERVED_NAMES = Set.of("context", "taf-context", "taf_context");

  public SutConnectionDescriptor {
    name = require(name, "name");
    if (!name.matches("[a-z][a-z0-9-]{0,62}"))
      throw new IllegalArgumentException("Invalid SUT connection name");
    if (RESERVED_NAMES.contains(name.toLowerCase(Locale.ROOT)))
      throw new IllegalArgumentException("Reserved SUT connection name: " + name);
    technology = require(technology, "technology");
    jdbcUrl = require(jdbcUrl, "jdbcUrl");
    username = username == null ? "" : username;
    passwordReference = passwordReference == null ? "" : passwordReference;
    mode = Objects.requireNonNull(mode, "mode");
    access = Objects.requireNonNull(access, "access");
    if (timeout == null || timeout.isZero() || timeout.isNegative())
      throw new IllegalArgumentException("timeout must be positive");
    cleanupPolicy = Objects.requireNonNull(cleanupPolicy, "cleanupPolicy");
    sensitiveColumns =
        sensitiveColumns == null
            ? Set.of()
            : sensitiveColumns.stream()
                .map(v -> v.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    cleanupSql = cleanupSql == null ? "" : cleanupSql.trim();
    if (access == DatabaseAccess.READ_ONLY && (setupAuthorized || cleanupAuthorized))
      throw new IllegalArgumentException(
          "Read-only connection cannot authorize setup or cleanup writes");
    if (cleanupPolicy == CleanupPolicy.AUTHORIZED_SQL && !cleanupAuthorized)
      throw new IllegalArgumentException("Cleanup SQL requires explicit authorization");
    if (!cleanupSql.isEmpty() && cleanupPolicy != CleanupPolicy.AUTHORIZED_SQL)
      throw new IllegalArgumentException("Cleanup SQL requires AUTHORIZED_SQL policy");
  }

  private static String require(String value, String field) {
    if (value == null || value.isBlank() || value.contains("\n") || value.contains("\r"))
      throw new IllegalArgumentException(field + " is invalid");
    return value;
  }

  @Override
  public String toString() {
    return "SutConnectionDescriptor[name="
        + name
        + ", technology="
        + technology
        + ", mode="
        + mode
        + ", access="
        + access
        + "]";
  }
}

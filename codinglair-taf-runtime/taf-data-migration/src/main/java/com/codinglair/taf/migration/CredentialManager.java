package com.codinglair.taf.migration;

import com.codinglair.taf.runtime.secret.SecretReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Owns short-lived database users and revokes them with their context lifecycle. */
public final class CredentialManager {
  private final MongoCredentialStore store;
  private final long expiryMs;
  private final Map<String, CredentialLease> leases = new ConcurrentHashMap<>();

  public CredentialManager(long expiryMs, MongoCredentialStore store) {
    if (expiryMs <= 0) throw new IllegalArgumentException("expiryMs must be positive");
    this.expiryMs = expiryMs;
    this.store = java.util.Objects.requireNonNull(store, "store");
  }

  public SecretReference create(String database, String username, String password, String leaseId) {
    String key = database + ":" + leaseId;
    CredentialLease lease =
        new CredentialLease(database, username, leaseId, System.currentTimeMillis() + expiryMs);
    if (leases.putIfAbsent(key, lease) != null) {
      throw new IllegalStateException(
          "Credential lease already exists for " + leaseId + " on database " + database);
    }
    try {
      store.createDatabaseUser(database, username, password);
      return SecretReference.parse("credential://mongodb/" + normalizeAlias(leaseId));
    } catch (RuntimeException failure) {
      leases.remove(key, lease);
      throw failure;
    }
  }

  public boolean delete(String leaseId) {
    var match =
        leases.entrySet().stream()
            .filter(entry -> entry.getValue().leaseId().equals(leaseId))
            .findFirst();
    if (match.isEmpty()) return false;
    CredentialLease lease = match.get().getValue();
    store.dropDatabaseUser(lease.database(), lease.username());
    return leases.remove(match.get().getKey(), lease);
  }

  public boolean exists(String leaseId) {
    return leases.values().stream().anyMatch(lease -> lease.leaseId().equals(leaseId));
  }

  public void cleanupExpired() {
    leases.values().stream()
        .filter(CredentialLease::expired)
        .map(CredentialLease::leaseId)
        .toList()
        .forEach(this::delete);
  }

  public void cleanupDatabase(String database) {
    leases.values().stream()
        .filter(lease -> lease.database().equals(database))
        .map(CredentialLease::leaseId)
        .toList()
        .forEach(this::delete);
  }

  public void cleanupAll() {
    leases.values().stream().map(CredentialLease::leaseId).toList().forEach(this::delete);
  }

  public int leaseCount() {
    return leases.size();
  }

  private static String normalizeAlias(String leaseId) {
    String alias = leaseId.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._/-]", "-");
    if (alias.isBlank()) throw new IllegalArgumentException("leaseId is invalid");
    return alias;
  }

  private record CredentialLease(String database, String username, String leaseId, long expiresAt) {
    boolean expired() {
      return System.currentTimeMillis() >= expiresAt;
    }
  }
}

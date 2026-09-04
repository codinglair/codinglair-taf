package com.codinglair.taf.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScopedCredentialLifecycleTest {
  @Test
  void createsRealDatabaseUserAndRevokesItWithoutEnvironmentPlaceholder() {
    var store = new RecordingStore();
    var manager = new CredentialManager(300_000, store);
    var reference = manager.create("context_db", "migration_user", "protected", "LEASE_1");

    assertThat(reference.scheme()).isEqualTo("credential");
    assertThat(store.events).containsExactly("create:context_db:migration_user");
    assertThat(manager.delete("LEASE_1")).isTrue();
    assertThat(store.events)
        .containsExactly("create:context_db:migration_user", "drop:context_db:migration_user");
  }

  @Test
  void cleanupDatabaseRevokesEveryDatabaseUser() {
    var store = new RecordingStore();
    var manager = new CredentialManager(300_000, store);
    manager.create("a", "u1", "p1", "L1");
    manager.create("a", "u2", "p2", "L2");
    manager.create("b", "u3", "p3", "L3");
    manager.cleanupDatabase("a");
    assertThat(manager.leaseCount()).isOne();
    assertThat(store.events).contains("drop:a:u1", "drop:a:u2").doesNotContain("drop:b:u3");
  }

  @Test
  void duplicateLeaseIsRejectedBeforeProvisioning() {
    var store = new RecordingStore();
    var manager = new CredentialManager(300_000, store);
    manager.create("a", "u", "p", "L1");
    assertThatThrownBy(() -> manager.create("a", "other", "p", "L1"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(store.events).containsExactly("create:a:u");
  }

  private static final class RecordingStore implements MongoCredentialStore {
    final List<String> events = new ArrayList<>();

    public void createDatabaseUser(String database, String username, String password) {
      events.add("create:" + database + ":" + username);
    }

    public void dropDatabaseUser(String database, String username) {
      events.add("drop:" + database + ":" + username);
    }
  }
}

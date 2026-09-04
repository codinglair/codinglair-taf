# TAF JDBC Database

## Governed lifecycle

DB-002 adds a technology-neutral lifecycle coordinator under
`com.codinglair.taf.database.lifecycle`. A connection-specific
`DatabaseLifecycleOperations` adapter joins environment provisioning/readiness, migration,
baseline reset, seeding, snapshot export, and cleanup without moving provisioning into a
controller. `LifecycleEvidenceSink` is the only snapshot/audit publication boundary and receives
snapshot content only after `DatabaseSnapshotSanitizer` has processed it.

| Policy | Preparation | Completion |
| --- | --- | --- |
| `EPHEMERAL` (default when omitted) | provision, readiness, migration, seed | cleanup for every terminal outcome |
| `RESET` | readiness, baseline reset, migration, seed | no implicit deletion of the shared instance |
| `SNAPSHOT_ON_FAILURE` | ephemeral preparation | sanitized snapshot once on non-pass, then cleanup |
| `RETAIN_WITH_TTL` | namespaced ephemeral preparation | attributed metadata retention, then idempotent expiry cleanup |
| `EXTERNAL` | no implicit operation | no implicit operation |

Production requests accept only `EXTERNAL`. TTL retention requires a positive duration, owner,
reason, Git revision, migration version, and an execution/database namespace. Cleanup failures are
audited as sanitized outcomes; when a test already failed, the lifecycle failure is attached as a
suppressed exception so it cannot replace the original failure.

`taf-database` provides session-owned JDBC controllers for explicitly named system-under-test
(SUT) databases. It does not provide framework context storage, provisioning, migrations, reset,
snapshots, or retention.

## Public contract

- `SutConnectionDescriptor` is the immutable technology-neutral connection contract.
- `SutConnectionRegistry` validates names and selects descriptors deterministically.
- `DatabaseController` supplies bounded query, update, transaction, setup, cleanup, and native JDBC
  operations. Obtain it with `session.getController(DatabaseController.class, "orders")`.
- `JdbcConnectionFactory` is the controlled adapter boundary. The default implementation resolves
  only a configured password reference at connection-open time.

Native connections are an advanced escape hatch. The controller tracks and closes them when the
owning `TestSession` closes; callers should still use try-with-resources as soon as practical.

## Configuration

The SUT registry has its own namespace. There is no default `DataSource` and no default database:

```yaml
taf:
  database:
    enabled: true
    environment: integration
    connections:
      orders:
        technology: postgresql
        jdbc-url: jdbc:postgresql://orders-db:5432/orders
        username: taf_reader
        password-reference: secret://env/ORDERS_DB_PASSWORD
        mode: external
        access: read-only
        timeout: 5s
        sensitive-columns: [email, access_token]
      customers:
        technology: postgresql
        jdbc-url: jdbc:postgresql://customers-db:5432/customers
        username: taf_writer
        password-reference: secret://env/CUSTOMERS_DB_PASSWORD
        mode: testcontainers
        access: read-write
        timeout: 10s
        setup-authorized: true
        cleanup-policy: authorized-sql
        cleanup-authorized: true
        cleanup-sql: delete from test_customer
```

Framework-owned context configuration is reserved under `taf.context.*`. If
`taf.context.jdbc-url` is supplied as a context identity guard, no named SUT connection may have the
same normalized identity. A context connection can never be selected implicitly through
`DatabaseController`, and a SUT connection is never registered as a context repository.

Read-only controllers reject update, setup, cleanup, and transactional writes before preparing the
statement. Setup and cleanup additionally require explicit per-connection authorization. External
connections never receive implicit cleanup; close-time SQL runs only under `AUTHORIZED_SQL` with
`cleanup-authorized: true`. Evidence contains logical name, technology, operation, and affected-row
count only. SQL, parameters, URLs, usernames, password references, credentials, and configured
sensitive column values are excluded.

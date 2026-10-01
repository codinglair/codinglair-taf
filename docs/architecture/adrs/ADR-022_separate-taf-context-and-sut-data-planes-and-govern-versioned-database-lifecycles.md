# ADR-022: Separate TAF Context and SUT Data Planes and Govern Versioned Database Lifecycles

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

**Context** MongoDB is the recommended TAF test-definition store, while consumer projects may test applications that use one or many MongoDB, relational, or other databases. Framework data and SUT persistence have different ownership, migration authority, isolation, and retention requirements. Reproducible automation also requires database changes and seed data to travel with the corresponding Git branch or release.

**Decision**

- Separate the framework-owned TAF context plane from the SUT persistence plane, including configuration namespaces, credentials, migration histories, lifecycle, and cleanup.
- Use a named SUT database connection registry that supports multiple connections and independent external or Testcontainers modes.
- Keep MongoDB as the recommended TAF context repository behind the existing SPI and keep file/Git providers first class.
- Use Flyway as the default migration and database script-versioning implementation behind `DataMigrationManager`.
- Store migration scripts in Git with automation code and payload metadata; treat Git as authoritative and databases as materialized state.
- Apply migrations per named connection. Automatically validate/migrate framework MongoDB during controlled preflight; require explicit authorization for external SUT databases.
- Prefer JSON Flyway migrations for MongoDB and enforce the selected connector's limitations and compatibility through pinned versions and integration tests.
- Support `EPHEMERAL`, `RESET`, `SNAPSHOT_ON_FAILURE`, `RETAIN_WITH_TTL`, and `EXTERNAL` lifecycle policies, with `EPHEMERAL` as the default and `SNAPSHOT_ON_FAILURE` preferred for investigation.
- Prohibit implicit production migration, destructive cleanup, indefinite retained working databases, and Testcontainers experimental reuse as a CI persistence contract.

**Consequences**

- Test projects can validate realistic microservice persistence without confusing SUT databases with TAF context storage.
- A branch or release can reproduce its code, test definitions, migrations, and payload references together.
- Retention remains available for analysis but becomes explicit, namespaced, time-limited, and auditable.
- Database adapters require migration, lifecycle, secret, readiness, and cleanup conformance tests.

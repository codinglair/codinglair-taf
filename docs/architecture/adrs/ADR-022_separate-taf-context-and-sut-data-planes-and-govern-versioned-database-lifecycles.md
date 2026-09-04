# ADR-022: Separate TAF Context and SUT Data Planes and Govern Versioned Database Lifecycles

**Status:** Accepted  
**Date:** 2026-08-13  
**Decision owners:** Product Owner and Solution Architecture  
**Scope:** TAF Runtime, consumer projects, database capability, test-definition providers, environment provisioning, and CI/CD

## Context

MongoDB is the recommended TAF test-definition store, while applications under test may use one or many MongoDB, relational, or other databases. This is common for microservice architectures where each service owns its persistence. Framework context data and SUT data have different owners, credentials, migration authority, lifecycle, cleanup, and retention requirements.

Automation code, test definitions, payload metadata, and database changes must also remain reproducible for a particular Git branch, tag, build, or release. A long-lived database must not become the undocumented authority for changes that are absent from source control.

Some teams need a clean database for isolation; others need limited retention for failure analysis. Retention must remain possible without allowing unmanaged persistent test databases to replace migration discipline.

## Decision

### Separate data planes

- Establish a **TAF context plane** for framework-owned test definitions, inputs, expected outputs, metadata, and payload references.
- Establish a **SUT persistence plane** for databases owned by the applications under test.
- Give the planes separate configuration namespaces, credentials, migration histories, readiness checks, lifecycle policies, and cleanup authority.
- Reserve the context-store identity so it is not implicitly exposed as a SUT database connection. A SUT MongoDB is not a TAF context store unless explicitly configured for that role.

### Context-store providers and payloads

- Keep MongoDB as the recommended primary TAF context repository behind the `TestDefinitionRepository` SPI established by ADR-008.
- Keep file/Git repositories as first-class providers for JSON, YAML, CSV, XML, PDF, image, and other project payloads.
- Store large binary payloads in Git or configured artifact/object storage by default; store their identifier, checksum, media type, version, and location in MongoDB.
- Permit GridFS as an optional adapter, not as the mandatory binary-storage model.

### Named SUT database registry

- Use logical, named database connections such as `orders`, `customers`, and `audit`.
- Support any number of connections, including multiple databases of the same technology.
- Configure each connection independently for technology, external or Testcontainers mode, access authority, secrets, migration, readiness, lifecycle, and cleanup.
- Make logical names stable in tests while physical connection properties remain environment-specific.

### Versioned migrations

- Use Flyway as the default migration and database script-versioning implementation behind a framework-neutral `DataMigrationManager` contract.
- Commit migration scripts to Git with automation code, test definitions, and payload metadata. Git is authoritative; databases are materialized state.
- Maintain an independent migration location and history for each named database.
- Automatically validate and migrate framework context MongoDB during controlled preflight. A required validation or migration failure blocks execution.
- Allow migrations for Testcontainers-managed SUT databases when enabled by the consumer project.
- Disable migration of external/shared SUT databases unless it is explicitly configured and authorized for the connection and environment.
- Prohibit production migration and destructive cleanup by default.
- Record the sanitized target identity, migration version, checksum, and outcome as execution evidence.

For MongoDB, JSON is the preferred Flyway migration format. JavaScript migrations are optional, require an approved `mongosh` runtime, do not provide the same transactional behavior, and must not be mixed with JSON migrations within one MongoDB migration project. Flyway and database-adapter versions shall be pinned and covered by compatibility and integration tests.

### Lifecycle and retention

Every framework-owned or Testcontainers-managed database declares one lifecycle policy:

- `EPHEMERAL`: provision from the declared baseline and destroy after execution; default for CI and isolated local runs.
- `RESET`: restore an approved shared instance to the declared baseline before execution.
- `SNAPSHOT_ON_FAILURE`: operate ephemerally but export sanitized diagnostic state before cleanup after a failure; preferred for analysis.
- `RETAIN_WITH_TTL`: retain namespaced data for an approved period with owner, reason, Git revision, migration version, and expiration metadata.
- `EXTERNAL`: do not provision, migrate, reset, or delete without separately authorized operations.

Testcontainers experimental reusable-container behavior is not the formal persistence or CI retention mechanism. Execution evidence and immutable diagnostic artifacts should be retained separately from mutable working databases.

## Consequences

- Consumer projects can test realistic microservice persistence using multiple independently managed databases.
- Framework context storage cannot be accidentally confused with SUT state.
- A Git branch or release can reproduce its automation code, definitions, migrations, and payload references together.
- Flyway provides the default implementation while `DataMigrationManager` preserves substitution for databases or environments with incompatible requirements.
- External database mutation becomes an explicit security and operational decision.
- Retention remains available but is namespaced, time-limited, attributable, and auditable.
- Database modules require tests for migration validation, lifecycle transitions, cleanup, retention expiration, secret isolation, and external-environment safeguards.

## Alternatives considered

- **Use one MongoDB for TAF context and SUT testing** — rejected because ownership, credentials, lifecycle, and migration authority would be ambiguous.
- **Make MongoDB mandatory for every test project** — rejected because file/Git definitions and direct payload files are often simpler and remain valid use cases.
- **Allow one unnamed database connection** — rejected because it does not support realistic microservice systems.
- **Let each project select migration tooling without a framework default** — rejected because it weakens reproducibility and scaffolding consistency.
- **Retain every container or database by default** — rejected because it encourages drift, consumes resources, and undermines clean reproducible baselines.
- **Always destroy all data** — rejected because controlled failure analysis and customer workflows sometimes require temporary retention.

## Compliance and verification

- Architecture tests shall prohibit context-store configuration from being consumed as an unnamed SUT database connection.
- Configuration binding tests shall cover multiple databases of the same and different technologies.
- Integration tests shall prove independent migration histories and lifecycle policies per named connection.
- CI shall verify an empty context MongoDB can be migrated to the current Git baseline.
- Golden projects shall demonstrate at least one framework MongoDB, two named SUT databases, mixed external/container modes, and failure-snapshot cleanup.
- External and production profiles shall prove migration and destructive cleanup are denied unless the required explicit policy and authorization are present.
- Reports shall include sanitized migration evidence and retained-data metadata without credentials or secret values.

## Related decisions and documents

- ADR-007: Separate environment provisioning from controllers and support Testcontainers
- ADR-008: Use MongoDB as the recommended test-definition store behind an SPI
- ADR-011: Keep secrets outside model and MCP context
- ADR-021: Normalize plaintext secrets at an explicit test-data authoring boundary
- Codinglair TAF Quality Intelligence SAD v1.10

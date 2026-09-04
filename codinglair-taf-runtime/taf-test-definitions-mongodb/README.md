# TAF MongoDB Test Definitions

Optional `TestDefinitionRepository` adapter for the reserved framework-context data plane. The
module is activated only when `taf.context.repository=mongodb`; file and CSV consumers do not need
this artifact or any MongoDB classes.

```properties
taf.context.repository=mongodb
taf.context.mongodb.uri=mongodb://localhost:27017
taf.context.mongodb.database=taf-context
taf.context.mongodb.collection=test-definitions
taf.context.mongodb.project=orders-acceptance
taf.context.mongodb.authority=git
taf.context.mongodb.schema-version=1
taf.context.mongodb.bootstrap-indexes=true
```

`project` is part of every definition identity and index. It must identify one independently
versioned consumer dataset. `authority` controls writes and synchronization; use `git` when the
database is a materialized execution store rather than the source of truth.

Startup may create only the two indexes declared in
`META-INF/taf/migrations/mongodb-test-definitions.json`. It validates `schemaVersion` but never
repairs or rewrites documents. Drift requires the future MIG-002 controlled migration path.

`MongoDefinitionSynchronizer` exports canonical JSON and imports it without silently overwriting
immutable versions. Snapshot project, authority, version sequence, and existing content conflicts
are validated before each write. Ordinary reads never normalize plaintext secrets; canonical
definitions must already contain approved `SecretReference` values.

# TAF Test Definitions

This Runtime module provides the provider-neutral `TestDefinitionRepository` SPI and the
MongoDB-independent CSV and JSON/YAML file providers. A project creates one CSV repository from a
`CsvTestDefinitionConfiguration` containing typed classpath or external-file locations, then
publishes one `TestDefinitionResolver` for its runners.

Input and expected-output CSV files are separate and must each contain the configured case-ID
column. Resolution joins exactly one row from each file and maps the remaining columns to the
requested Java record types. Duplicate IDs fail when the repository is created; missing IDs,
malformed data, and conversion failures produce a categorized `DefinitionDiagnosticException`.

Persistent `TestDefinition` values contain inputs and expected outputs only. Actual outputs and
execution metadata use `TestExecutionData`, and credentials are represented only by opaque
`SecretReference` aliases. TestNG consumers call `TafBaseTest.testDefinition(...)`; Cucumber glue
calls `CucumberScenarioSession.testDefinition(...)` with the same project resolver.

Classified secret fields use `@SecretField` or CSV schema metadata. Ordinary repository construction
enforces approved references and never mutates its sources. Plaintext preparation is a separate,
authorized `CsvSecretNormalizer` operation restricted to local or approved-staging requests. It
validates the complete canonical result, detects concurrent changes, and uses atomic replacement;
read-only and classpath sources support enforcement only. The neutral `SecretProvisioner` creates
references and remains separate from PWD-001 execution-time resolution.

`FileTestDefinitionRepository` imports and atomically exports a deterministic, schema-versioned
JSON or YAML document. Every lifecycle change appends an immutable version. The SPI rejects stale
expected versions, simultaneous file writes, and writers that do not match the configured
`RepositoryAuthority`. Explicit correlation metadata supports deterministic lookup.

`VersionedTestDefinition` adds correlation and `PayloadReference` metadata without changing the
accepted DATA-001R `TestDefinition` record shape. Payload references validate logical ID, URI,
SHA-256 checksum, media type, byte size, and source version without reading or interpreting binary
content. The reusable `TestDefinitionRepositoryContract` is published in the module test JAR for
DATA-002 and later providers. DATA-003 payload resolution is exposed through the additive
`PayloadResolver`, `ResolvedPayload`, `PayloadRange`, and metadata-only `PayloadEvidence`
contracts. File/Git and optional GridFS implementations remain in their adapter modules.

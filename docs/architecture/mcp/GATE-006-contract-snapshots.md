# GATE-006 Runtime/MCP contract snapshots

Snapshot date: 2026-08-24. Baseline artifact version: <!-- taf-version -->`1.0.0`. MCP schema major:
`1.0`; capability catalog: `1.0.0`; Spring AI MCP server identity: `codinglair-taf/1.0.0`.

## Runtime public-contract baseline

The accepted Runtime baseline remains the published module API recorded by the Legacy Contract
Compatibility Matrix. GATE-006 changes no Runtime type, method, serialized value, configuration
key, lifecycle contract, or migration. The full 41-project clean gate compiles all Runtime
consumers and golden projects on Java 25.

## MCP schema baseline

Authoritative schemas remain under `taf-mcp-contracts/src/main/resources/META-INF/taf/mcp/schema/v1`:

- capability catalog, tool request/response, resource, prompt, and error schemas;
- stable `urn:codinglair:taf:mcp:schema:v1:*` identifiers;
- valid and invalid compatibility fixtures under `src/test/resources/fixtures/v1`.

The supported coarse-grained operations are discovery, validate, scaffold, compile, build,
execute, inspect/retrieve/report, cancel, and diagnose. Existing v1 instances remain valid.

## External transport surface

Both transports expose the same MCP-010 additions:

- prompts: `taf.qa.failure-analysis`, `taf.qa.execution-summary`,
  `taf.qa.environment-triage`;
- resource templates: `taf://report/{jobId}` and
  `taf://evidence/{jobId}/{evidenceId}`;
- tool: `diagnose`, accepting only the closed `DiagnosticKind` enumeration.

STDIO owns local identity/workspace configuration. HTTP derives identity, project, environment,
roles, and scopes only from an authenticated JWT. This intentional authorization-context
difference does not alter the shared schema-v1 results.

## Compatibility decision

The surface is additive. No schema major bump or Runtime migration is required. Future removal,
rename, new required field, narrowed accepted value, or strengthened side-effect/approval semantic
must follow the MCP versioning rules and update this snapshot, fixtures, and migration guidance.

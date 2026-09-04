# ADR-021: Normalize Plaintext Secrets at an Explicit Test-Data Authoring Boundary

**Status:** Accepted  
**Date:** 2026-08-10  
**Decision Owners:** Codinglair TAF Architecture  
**Related Documents:** SAD v1.9; ADR-011; PWD-001 Secret Handling

## Context

Test automation engineers commonly author CSV, JSON, YAML, SQL, and database fixtures directly. A runtime-only policy that rejects plaintext secret values is safe but creates unnecessary manual encryption work; a universal auto-encryption rule is impossible because direct file and database writes can bypass TAF. Making ordinary repository reads silently mutate Git-managed files or shared test-definition stores would violate read expectations, complicate transactions and auditability, and create unsafe concurrent execution behavior.

ADR-011 governs secret reference resolution and the prohibition on resolved values entering models, MCP, prompts, logs, reports, or artifacts. This ADR governs the distinct authoring and ingestion path.

## Decision

- Classify secret-bearing fields explicitly through definition schema metadata or `@SecretField(kind = SecretKind...)`; do not infer classification from field names.
- Maintain a canonical representation in which every `SECRET` field contains an approved `SecretReference`, never plaintext. Persistent names should make the contract clear, for example `passwordReference` or `password_reference`.
- Permit plaintext only in an explicitly invoked authoring, import, migration, provisioning, or normalization operation. This transient authoring representation must not be logged, reported, cached, included in `TestSession`, attached as evidence, or sent to MCP/LLM.
- Provide two policies: `ENFORCE` (default; reject plaintext) and `NORMALIZE_AND_PERSIST` (explicit local or approved staging preparation; protect through the configured provider and write back the resulting reference).
- Run normalization before execution as a distinct preparation/preflight phase. It shall never be an implicit side effect of an ordinary repository read.
- Require atomic file replacement or transactional database persistence for write-back. When a source is read-only, a write fails, or a provider cannot protect the value, execution fails safely.
- Keep `SecretProvider`/`SecretManager` as read/execution contracts. A separate `SecretProvisioner` owns creation/protection and returns the canonical reference.
- Permit metadata-only audit messages stating that a classified secret field was protected; never include plaintext, ciphertext, encryption keys, or resolved values.

## Consequences

- Direct SQL and file authoring remain possible, while TAF refuses to knowingly execute persisted plaintext declared as a secret.
- Local Jasypt and environment providers can support the first vertical slice; enterprise vault/cloud provisioning can be added behind the same provisioning boundary later.
- CI and shared environments default to deterministic rejection rather than silently changing source data.
- Importers, test-definition providers, migration tooling, preflight, redaction, and leak-test suites must enforce the same classification and reference grammar.
- Full credential-administration UI, general-purpose secret lifecycle management, and test-definition-store implementation remain separate scope from PWD-001 foundational runtime resolution.

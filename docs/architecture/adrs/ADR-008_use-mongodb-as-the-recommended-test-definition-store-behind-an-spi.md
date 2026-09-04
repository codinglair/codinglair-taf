# ADR-008: Use MongoDB as the recommended test-definition store behind an SPI

**Status:** Accepted  
**Date:** 2026-07-24  
**Decision owners:** Product Owner and Solution Architecture  
**Scope:** Codinglair TAF product family

## Context

Nested multi-channel E2E inputs and expected outcomes are awkward in CSV. MongoDB fits flexible documents and agent-generated definitions, but making it mandatory would burden small or Git-centric consumers.

## Decision

- Define a pluggable TestDefinitionRepository.
- Provide MongoDB as the recommended default.
- Support file/Git and alternative database providers.
- Separate persistent test definitions, transient TestSession state, execution metadata, and large artifacts.
- Use immutable definition versions and draft, review, approved, superseded, and archived states.
- Configure one authoritative source per project.
- Use JSON/YAML import and export for Git-compatible review.

## Consequences

- Complex definitions become queryable and versioned.
- Teams can operate without MongoDB.
- Synchronization and authority conflicts must be explicit.
- MongoDB Testcontainer support is required for framework verification.

## Alternatives considered

- MongoDB mandatory — rejected because it harms portability.
- CSV only — rejected because it does not scale to nested E2E data.
- Store runtime objects in MongoDB — rejected because active handles and secrets do not belong in persistent definitions.

## Compliance and verification

- Architecture and dependency tests shall enforce machine-verifiable boundaries.
- The applicable implementation assignments shall include unit, integration, contract, security, and compatibility tests.
- Public contract or schema changes shall update the Legacy Contract Compatibility Matrix and migration guidance.
- Deviations require a superseding ADR or an explicitly approved amendment.

## Related documents

- Test Automation Framework and Quality Intelligence Platform BRD v1.0
- Codinglair TAF and Quality Intelligence SAD v1.0
- Codinglair TAF Engineering and DevOps Implementation Plan v1.0
- Legacy Contract Compatibility Matrix, when created
- ADR-022 defines separation of TAF and SUT data planes, named database connections, Flyway migrations, and database lifecycle policy.

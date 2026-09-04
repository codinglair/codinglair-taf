# ADR-007: Separate environment provisioning from controllers and support Testcontainers

**Status:** Accepted  
**Date:** 2026-07-24  
**Decision owners:** Product Owner and Solution Architecture  
**Scope:** Codinglair TAF product family

## Context

Controllers need databases, brokers, identity services, mock servers, and application infrastructure. If controllers start Docker resources themselves, deterministic interaction logic becomes coupled to provisioning and cannot target external environments cleanly.

## Decision

- Introduce EnvironmentResource, EnvironmentProvider, and EnvironmentRegistry abstractions.
- Use Testcontainers providers for disposable local and CI infrastructure.
- Let the same controller configuration target container-provisioned or external resources.
- Support dependency ordering, shared networks, health, dynamic properties, initialization, scoped reuse, logs, and cleanup.
- Require policy approval for untrusted images.
- Use Testcontainers as a recommended option, not a mandatory execution model.

## Consequences

- Controllers remain focused on test interaction.
- Local/CI tests gain reproducible disposable infrastructure.
- External customer environments remain supported.
- Docker availability and image governance become explicit prerequisites.

## Alternatives considered

- Embed Testcontainers in each controller — rejected because it couples provisioning and interaction.
- Require external environments — rejected because it increases setup and test isolation cost.

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
- ADR-022 defines database-specific migration, retention, and external/container lifecycle policy.

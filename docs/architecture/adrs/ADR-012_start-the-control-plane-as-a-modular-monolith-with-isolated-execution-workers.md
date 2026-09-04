# ADR-012: Start the control plane as a modular monolith with isolated execution workers

**Status:** Accepted  
**Date:** 2026-07-24  
**Decision owners:** Product Owner and Solution Architecture  
**Scope:** Codinglair TAF product family

## Context

The initial product does not require distributed execution and should avoid premature microservice operational cost. However, generated-code compilation, browsers, containers, and customer tests form a strong trust and resource boundary.

## Decision

- Deploy the control plane initially as a modular Spring Boot application where practical.
- Enforce internal module boundaries that allow later service extraction.
- Run compilation and test execution in isolated worker processes/jobs.
- Apply constrained workspace, process identity, quotas, allowlists, cancellation, and artifact return at the worker boundary.
- Keep job, approval, audit, model, ingestion, and artifact contracts location independent.

## Consequences

- Initial deployment remains manageable.
- Risky execution is isolated early.
- Later extraction is possible without redesigning external contracts.
- Worker lifecycle and secure transport add complexity from the beginning.

## Alternatives considered

- Microservices from day one — rejected as unnecessary operational complexity.
- Run generated code inside the API/MCP process — rejected as an unacceptable trust and stability risk.

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

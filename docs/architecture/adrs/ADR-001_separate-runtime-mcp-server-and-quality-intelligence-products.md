# ADR-001: Separate Runtime, MCP Server, and Quality Intelligence products

**Status:** Accepted  
**Date:** 2026-07-24  
**Decision owners:** Product Owner and Solution Architecture  
**Scope:** Codinglair TAF product family

## Context

The product vision combines deterministic automation, standardized agent access, and proprietary AI-assisted quality workflows. Treating them as one inseparable application would couple community adoption to AI, blur licensing boundaries, and prevent external agents from using the Runtime independently.

## Decision

- Create three explicit product boundaries: TAF Runtime, TAF MCP Server, and Quality Intelligence Platform.
- TAF Runtime is deterministic and independently usable.
- TAF MCP Server depends on Runtime and is usable by standards-compliant external agents.
- Quality Intelligence consumes public Runtime/MCP contracts and may use external MCP servers.
- Community modules must not depend on proprietary modules.

## Consequences

- Runtime and MCP can be adopted without the commercial platform.
- Licensing, packaging, dependency, and release boundaries become enforceable.
- Some contracts and integration tests must be maintained across products.

## Alternatives considered

- One monolithic product — rejected because it creates AI, licensing, and deployment coupling.
- Runtime plus proprietary-only MCP — rejected because it blocks ecosystem interoperability.

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

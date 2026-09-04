# ADR-004: Use a typed named controller registry within TestSession

**Status:** Accepted  
**Date:** 2026-07-24  
**Decision owners:** Product Owner and Solution Architecture  
**Scope:** Codinglair TAF product family

## Context

A single active controller in TestLifecycleManager prevents E2E scenarios that combine API setup, messaging, UI interaction, database validation, and mobile behavior. A raw string map would allow composition but weaken type safety and lifecycle control.

## Decision

- Create one isolated TestSession per test invocation/scenario.
- Expose typed and named controller lookup.
- Support multiple named instances of the same controller type and one configurable default.
- Initialize controllers lazily on acquisition.
- Register acquired resources for reverse-order session cleanup.
- Keep the underlying map as an implementation detail.

## Consequences

- Multi-channel E2E tests become natural.
- Expensive resources are created only when used.
- Ambiguous or invalid lookups fail early.
- Session and shared resource scopes must be implemented carefully for concurrency.

## Alternatives considered

- Keep one controller — rejected because it blocks E2E composition.
- Expose Map<String, TestController> directly — rejected because it loses type validation and encapsulation.

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

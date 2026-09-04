# ADR-010: Write generated code as proposed working-tree changes without autonomous commits

**Status:** Accepted  
**Date:** 2026-07-24  
**Decision owners:** Product Owner and Solution Architecture  
**Scope:** Codinglair TAF product family

## Context

The agent must write code into the project to compile, execute, analyze, and repair it. Display-only output cannot support this loop. Autonomous commits or pushes would take source-control authority away from the user.

## Decision

- Allow authorized file changes in the checked-out working tree.
- Present generated code as a proposed diff.
- Do not commit, push, tag, create pull requests, or publish releases autonomously in the initial release.
- Treat dependency addition as a separate approval from ordinary file modification.
- Honor existing user changes and repository policies.
- Allow project-level page objects, clients, models, tasks, and helpers; report missing Runtime abstractions as capability gaps.
- Require generated and migrated consumer assets to use approved project blueprints and pass ADR-020 conformance gates before acceptance.
- Prohibit consumer-test assignments from changing Runtime contracts or introducing controllers without separately approved scope.

## Consequences

- The agent can compile and repair real code.
- Users retain source-control approval.
- Working-tree isolation and change provenance are required.
- The platform cannot rely on commits as its own checkpoint mechanism.

## Alternatives considered

- Display/export only — rejected because generated code cannot be verified in place.
- Direct commit/push — rejected because it is unnecessarily consequential.

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
- ADR-019: Standardize consumer-project blueprints and test-design patterns
- ADR-020: Enforce conformance for agent-generated and migrated test assets

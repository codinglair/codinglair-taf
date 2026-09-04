# ADR-016: Defer detailed Quality Intelligence assignments until Runtime and MCP stabilize

**Status:** Accepted  
**Date:** 2026-07-24  
**Decision owners:** Product Owner and Solution Architecture  
**Scope:** Codinglair TAF product family

## Context

Quality Intelligence depends on Runtime capabilities, MCP schemas, job execution, evidence, security, approvals, audit, and model boundaries. Detailed early decomposition would encode assumptions and cause rework while the foundation is changing.

## Decision

- Include Quality Intelligence as high-level epics in the current engineering plan.
- Do not create detailed implementation assignments equal to Runtime/MCP yet.
- Permit decomposition only after Runtime public contracts are stable, MCP tools/resources are versioned, job/worker/approval/audit/evidence models operate, model/data policies are enforceable, and an E2E Runtime-to-MCP flow is accepted.
- Preserve proprietary product and licensing boundaries during planning.

## Consequences

- The strategic product direction remains visible.
- Runtime and MCP receive implementation focus.
- Detailed Quality Intelligence delivery is intentionally delayed.
- Epic estimates remain coarse until the decomposition gate is met.

## Alternatives considered

- Detailed assignments now — rejected because foundational contracts are not stable.
- Exclude Quality Intelligence entirely — rejected because dependencies and product direction would be lost.

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

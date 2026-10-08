# ADR-016: Defer Detailed Quality Intelligence Assignments until Runtime and MCP Stabilize

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

**Context** Quality Intelligence depends on Runtime capabilities, MCP schemas, job execution, evidence, security, approvals, audit, and model boundaries. Detailed early decomposition would encode assumptions and cause rework while the foundation is changing.

**Decision**

- Include Quality Intelligence as high-level epics in the current engineering plan.
- Do not create detailed implementation assignments equal to Runtime/MCP yet.
- Permit decomposition only after Runtime public contracts are stable, MCP tools/resources are versioned, job/worker/approval/audit/evidence models operate, model/data policies are enforceable, and an E2E Runtime-to-MCP flow is accepted.
- Preserve proprietary product and licensing boundaries during planning.

**Consequences**

- The strategic product direction remains visible.
- Runtime and MCP receive implementation focus.
- Detailed Quality Intelligence delivery is intentionally delayed.
- Epic estimates remain coarse until the decomposition gate is met.

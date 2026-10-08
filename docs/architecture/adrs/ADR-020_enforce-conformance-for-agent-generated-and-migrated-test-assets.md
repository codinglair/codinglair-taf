# ADR-020: Enforce Conformance for Agent-Generated and Migrated Test Assets

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

**Decision**

- Treat agent output as proposed changes until automated conformance gates and human review pass.
- Validate blueprint/schema compliance, architecture, lifecycle, dependencies, configuration, security, traceability, reporting, and capability-specific execution.
- Enforce deterministic structural rules automatically while routing subject-aware abstraction judgments to agent/human semantic review rather than universal static rules.
- Require migration compatibility matrices and prohibit silent removal of demonstrated behavior.
- Require a blocked handoff or separate framework assignment when a needed Runtime capability is absent.

**Consequences**

- Compilation or a passing happy-path test is no longer sufficient evidence of completion.
- Architecture knowledge moves from prompts into templates, schemas, capability manifests, executable rules, and golden projects.
- Initial agent autonomy remains intentionally limited until repeatable conformance is demonstrated.

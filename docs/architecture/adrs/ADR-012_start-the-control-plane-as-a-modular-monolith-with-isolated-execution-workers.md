# ADR-012: Start the Control Plane as a Modular Monolith with Isolated Execution Workers

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

**Context** The initial product does not require distributed execution and should avoid premature microservice operational cost. However, generated-code compilation, browsers, containers, and customer tests form a strong trust and resource boundary.

**Decision**

- Deploy the control plane initially as a modular Spring Boot application where practical.
- Enforce internal module boundaries that allow later service extraction.
- Run compilation and test execution in isolated worker processes/jobs.
- Apply constrained workspace, process identity, quotas, allowlists, cancellation, and artifact return at the worker boundary.
- Keep job, approval, audit, model, ingestion, and artifact contracts location independent.

**Consequences**

- Initial deployment remains manageable.
- Risky execution is isolated early.
- Later extraction is possible without redesigning external contracts.
- Worker lifecycle and secure transport add complexity from the beginning.

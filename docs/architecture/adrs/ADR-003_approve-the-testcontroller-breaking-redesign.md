# ADR-003: Approve the TestController Breaking Redesign

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

**Context** The legacy TestController contract contains setup, teardown, and browser-oriented attachment methods. It cannot represent typed health, lifecycle states, multiple artifacts, or technology-neutral controllers. The new repository is not consumed by external projects, so source compatibility provides little value.

**Decision**

- Replace the legacy TestController contract with identity, state, initialize, health, artifact collection, and close semantics.
- Use typed subinterfaces for technology-specific behavior.
- Acquire typed and named instances lazily through `ControllerRegistry` within `TestSession`.
- Require thread-safe at-most-once successful initialization, observable state, typed secret-safe health, idempotent close, reverse-order cleanup, and preservation of all cleanup failures.
- Route typed evidence only through the sanitized `ArtifactCollector` boundary; controllers never persist or attach artifacts directly to a reporter.
- Require Runtime core, runners, auto-configuration, implementations, and tests to use the replacement lifecycle exclusively. Parallel retention of an operational legacy lifecycle is non-compliant.
- Do not implement a legacy TestController adapter unless separately approved.
- Update the Sauce demonstration project.
- Create and maintain a Legacy Contract Compatibility Matrix for all public contracts.

**Consequences**

- The base contract becomes technology neutral.
- The demo and legacy implementations require migration.
- This is an approved breaking change; other breaks still require review.
- An assignment cannot be accepted merely because new lifecycle types exist while operational code still exposes the legacy contract.

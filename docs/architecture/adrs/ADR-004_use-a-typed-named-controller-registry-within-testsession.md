# ADR-004: Use a Typed Named Controller Registry Within TestSession

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

**Context** A single active controller in TestLifecycleManager prevents E2E scenarios that combine API setup, messaging, UI interaction, database validation, and mobile behavior. A raw string map would allow composition but weaken type safety and lifecycle control.

**Decision**

- Create one isolated TestSession per test invocation/scenario.
- Expose typed and named controller lookup.
- Support multiple named instances of the same controller type and one configurable default.
- Initialize controllers lazily on acquisition.
- Register acquired resources for reverse-order session cleanup.
- Keep the underlying map as an implementation detail.

**Consequences**

- Multi-channel E2E tests become natural.
- Expensive resources are created only when used.
- Ambiguous or invalid lookups fail early.
- Session and shared resource scopes must be implemented carefully for concurrency.

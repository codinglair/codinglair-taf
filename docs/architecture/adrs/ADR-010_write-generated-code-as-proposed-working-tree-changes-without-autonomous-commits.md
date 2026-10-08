# ADR-010: Write Generated Code as Proposed Working-Tree Changes without Autonomous Commits

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

**Context** The agent must write code into the project to compile, execute, analyze, and repair it. Display-only output cannot support this loop. Autonomous commits or pushes would take source-control authority away from the user.

**Decision**

- Allow authorized file changes in the checked-out working tree.
- Present generated code as a proposed diff.
- Do not commit, push, tag, create pull requests, or publish releases autonomously in the initial release.
- Treat dependency addition as a separate approval from ordinary file modification.
- Honor existing user changes and repository policies.
- Allow project-level page objects, clients, models, tasks, and helpers; report missing Runtime abstractions as capability gaps.

**Consequences**

- The agent can compile and repair real code.
- Users retain source-control approval.
- Working-tree isolation and change provenance are required.
- The platform cannot rely on commits as its own checkpoint mechanism.

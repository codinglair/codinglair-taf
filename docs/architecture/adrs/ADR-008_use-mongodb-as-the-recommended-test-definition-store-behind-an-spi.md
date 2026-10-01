# ADR-008: Use MongoDB as the Recommended Test-Definition Store Behind an SPI

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

**Context** Nested multi-channel E2E inputs and expected outcomes are awkward in CSV. MongoDB fits flexible documents and agent-generated definitions, but making it mandatory would burden small or Git-centric consumers.

**Decision**

- Define a pluggable TestDefinitionRepository.
- Provide MongoDB as the recommended default.
- Support file/Git and alternative database providers.
- Separate persistent test definitions, transient TestSession state, execution metadata, and large artifacts.
- Use immutable definition versions and draft, review, approved, superseded, and archived states.
- Configure one authoritative source per project.
- Use JSON/YAML import and export for Git-compatible review.

**Consequences**

- Complex definitions become queryable and versioned.
- Teams can operate without MongoDB.
- Synchronization and authority conflicts must be explicit.
- MongoDB Testcontainer support is required for framework verification.

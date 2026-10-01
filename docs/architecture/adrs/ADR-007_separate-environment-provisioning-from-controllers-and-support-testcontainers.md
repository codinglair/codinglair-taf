# ADR-007: Separate Environment Provisioning from Controllers and Support Testcontainers

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

**Context** Controllers need databases, brokers, identity services, mock servers, and application infrastructure. If controllers start Docker resources themselves, deterministic interaction logic becomes coupled to provisioning and cannot target external environments cleanly.

**Decision**

- Introduce EnvironmentResource, EnvironmentProvider, and EnvironmentRegistry abstractions.
- Use Testcontainers providers for disposable local and CI infrastructure.
- Let the same controller configuration target container-provisioned or external resources.
- Support dependency ordering, shared networks, health, dynamic properties, initialization, scoped reuse, logs, and cleanup.
- Require policy approval for untrusted images.
- Use Testcontainers as a recommended option, not a mandatory execution model.

**Consequences**

- Controllers remain focused on test interaction.
- Local/CI tests gain reproducible disposable infrastructure.
- External customer environments remain supported.
- Docker availability and image governance become explicit prerequisites.

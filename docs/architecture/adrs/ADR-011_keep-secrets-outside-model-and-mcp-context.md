# ADR-011: Keep Secrets Outside Model and MCP Context

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

**Context** The framework requires credentials for systems under test, Jira, databases, brokers, cloud services, and model providers. Jasypt alone does not provide enterprise rotation, audit, dynamic credentials, or safe AI isolation.

**Decision**

- Represent secrets as references or credential-profile aliases.
- Resolve secrets only inside an authorized deterministic execution boundary.
- Never return resolved secrets to LLMs, MCP clients, logs, reports, prompts, or artifacts.
- Define a SecretProvider SPI for Vault, cloud secret managers, Kubernetes Secrets, environment variables, and local Jasypt fallback.
- Prefer short-lived credentials and workload/managed identities.
- Separate human, agent, worker, workload, and test-persona identities.
- Use OIDC/OAuth and role/project/environment/action/agent-aware authorization.
- Keep secret creation and test-data normalization outside the execution resolution SPI; ADR-021 governs the explicit authoring and ingestion boundary.

**Consequences**

- Enterprise secret systems and offline/local modes can coexist.
- Jasypt remains a limited development/backward-compatibility option.
- Every integration must implement redaction and secret-leak tests.
- Credential resolution must be auditable without recording values.
- Canonical test-definition secret references are consumed by this decision; their creation and validation are governed by ADR-021.

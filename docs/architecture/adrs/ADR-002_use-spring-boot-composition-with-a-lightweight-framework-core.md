# ADR-002: Use Spring Boot Composition with a Lightweight Framework Core

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

**Context** The legacy framework uses class-name factories and property loading. The target needs typed configuration, optional modules, security, MCP, data, and integration support, while consumer test libraries must not be forced to become server applications.

**Decision**

- Use Spring Boot 4.x for auto-configuration, typed properties, integration wiring, and platform services.
- Keep core contracts lightweight and independent of Spring where practical.
- Optional modules contribute conditional auto-configuration.
- Configure logical capability instances and aliases rather than implementation class names.
- Use Java 25, compatible Spring AI 2.x, Maven Wrapper, and pinned BOM/plugin versions.

**Consequences**

- Spring supplies mature composition and configuration.
- Core modules remain independently consumable and testable.
- Auto-configuration and context tests become required.
- Care is required to keep Spring types from leaking into public core contracts.

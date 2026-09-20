# ADR-027: Compose Projects from a Common Blueprint and Capability Contributions

- **Status:** Accepted
- **Date:** 2026-09-14
- **Target release:** Codinglair TAF 1.2.0
- **Related requirements:** FR-SCF-001–FR-SCF-008; FR-MCP-019–FR-MCP-021

## Context

The existing blueprint was created around Playwright. Codinglair TAF now scaffolds Web, API, Database, Messaging, and Mobile projects and combinations of those capabilities. Playwright-specific assumptions cannot remain in common project generation.

## Decision

Refactor the blueprint into a versioned common foundation plus composable capability contributions. The common foundation owns Maven/Java/Spring Boot structure, Java 25 baseline, profiles, environment/secrets skeleton, TestSession, TestNG baseline, Allure integration, common resources, and shared validation.

Web, API, Database, Messaging, and Mobile contributions add only their starter, typed configuration, package structure, examples, and capability preflight rules. Contributions declare file and configuration ownership. Collisions or unsupported combinations fail before generation.

Mobile defaults to Android/UiAutomator2 when no platform is supplied. An explicit iOS request fails because iOS is architecturally accommodated but not implemented. Device infrastructure remains user/provider-owned.

MCP normalizes and validates requests, selects the approved blueprint, invokes composition, and runs conformance. Dependency topology remains authoritative in starter POMs and the BOM. Generated projects do not acquire MCP dependencies merely because MCP generated them.

Release 1.2.0 generates Maven projects only. Gradle dependency declarations may be documented and syntactically validated for consumers of the Java artifacts.

## Consequences

- Existing blueprint content is classified as common, capability-specific, provider-specific, runner/reporting, or obsolete.
- New capabilities add contributions instead of modifying an unconstrained generator.
- Deterministic output and ownership rules prevent fragment overwrites.
- Web retains POM/PCOM conventions; API, Mobile, and other capabilities gain appropriate patterns without inheriting Web structure.

## Alternatives considered

- Maintain separate full project templates per combination: rejected because combinations multiply and drift.
- Let MCP generate dependencies directly: rejected because it duplicates the Maven dependency graph.
- Keep Playwright as the common template: rejected because non-Web projects would receive irrelevant structure and dependencies.


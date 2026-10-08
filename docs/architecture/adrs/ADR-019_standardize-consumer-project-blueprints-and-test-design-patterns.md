# ADR-019: Standardize Consumer Project Blueprints and Test Design Patterns

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

**Decision**

- Publish versioned capability-oriented consumer-project blueprints rather than allowing agents to invent unconstrained project structures.
- Make a standard non-web Spring Boot application the default supported consumer-project blueprint while retaining public Runtime SPIs for explicitly chosen non-Spring integration.
- Define responsibility boundaries for POM, PCOM, TAF-defined AOM, mobile screen objects, messaging workflows, database repository/query objects, application services, validators, and executable tests.
- Keep application-specific abstractions in consumer projects and framework lifecycle/controller/reporting implementations in TAF Runtime.
- Keep `TafBaseTest`, `TafCucumberHooks`, `TestSessionLifecycle`, Cucumber-TestNG integration, and lifecycle behavior in framework runner modules; scaffolding selects and wires these modules but never copies them.
- Require scaffolding to generate the Spring Boot application, selected starters, typed configuration, local/CI profiles, unresolved-value handling, preflight validation, standard extension package, and architecture/context-loading tests.
- Centralize locators in their owning page, screen, or component objects and prohibit session-bound static locator instances or selector construction inside action methods.
- Require subject-aware abstraction: consolidated workflow/service calls for prerequisites and repeated flows, explicit object actions when that behavior is the test subject, and explicit steps when semantic intent is uncertain.
- Keep reporting/preflight/test-definition lifecycle inside framework base tests and hooks; consumer tests shall not implement lifecycle wrappers or hide dependencies behind a generic service locator.
- Maintain focused golden examples, cross-capability examples, and an eventual comprehensive reference implementation.

**Consequences**

- Human and agent authors receive consistent, teachable extension points.
- New capabilities require blueprint and reference-project maintenance.
- Structure remains adaptable while dependency and responsibility rules are enforceable.
- Java developers can extend consumer projects through standard Spring beans and configuration without modifying TAF Runtime.

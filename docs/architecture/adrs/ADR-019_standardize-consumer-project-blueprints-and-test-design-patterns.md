# ADR-019: Standardize consumer-project blueprints and test-design patterns

**Status:** Accepted  
**Date:** 2026-08-03  
**Last updated:** 2026-08-05  
**Decision owners:** Product Owner and Solution Architecture  
**Scope:** TAF consumer projects, examples, scaffolding, and agent-assisted generation

## Context

TAF must support consumer projects combining web, API, asynchronous messaging, database, mobile, and future AI-agent testing. A migration of the legacy Playwright demonstration preserved executable behavior but lost important project organization, configuration, CSV data, traceability, lifecycle, and reporting patterns. This demonstrated that a coding agent cannot be expected to infer the intended consumer architecture reliably, even when source examples exist.

The framework needs teachable and machine-enforceable extension points without forcing all application-specific abstractions into Runtime or imposing one irrelevant package tree on every project.

Consumer projects also need a conventional extension and support model. Making each project invent framework bootstrap, configuration binding, runner integration, or third-party wiring would recreate the errors that Spring Boot is intended to remove.

The corrected Playwright demo showed two additional risks: selectors embedded directly inside action methods make maintenance harder, while excessive workflow consolidation and consumer-owned reporting wrappers make tests unreadable and leak framework plumbing. The blueprint must distinguish deterministic structural rules from the semantic decision about what behavior a test is actually exercising.

## Decision

- Publish versioned, capability-oriented consumer-project blueprints. Agents and official scaffolding use these blueprints instead of inventing unconstrained repository structures.
- Make a standard Spring Boot application the default and primary supported consumer-project blueprint. It runs as a non-web application unless a selected use case requires an embedded server.
- Keep TAF Runtime core lightweight according to ADR-002. Public Runtime SPIs may support deliberate non-Spring consumers, but official scaffolding and golden projects use Spring Boot by default.
- Separate three responsibility layers:
  1. TAF Runtime owns lifecycle, controllers, configuration SPI, reporting, sanitization, artifacts, and test-definition provider contracts.
  2. Consumer automation abstractions own application-specific pages, components, API resources, mobile screens, event workflows, repositories, services, models, and validators.
  3. Executable tests own scenario intent, orchestration, traceability, and assertions.
- Recognize these consumer design patterns:
  - Page Object Model (POM) for cohesive web pages.
  - Page Component Object Model (PCOM) for reusable or complex page components.
  - API Object Model (AOM), defined by TAF as an API resource or cohesive endpoint group encapsulating typed request construction, controller invocation, and response mapping.
  - Screen Object Model and reusable components for mobile applications.
  - Messaging objects/workflows for topics, queues, channels, and typed business events.
  - Database repository/query objects for application-specific persistence access.
  - Future agent-task and evaluation workflows for AI-agent testing.
- Treat AOM as a TAF convention because the term is less standardized than POM; this ADR defines its meaning within TAF.
- Require one authoritative locator declaration in the object that owns the element:
  - page/screen-specific locators are `private static final` immutable specifications in the POM or Screen Object;
  - reusable-component locators belong in the PCOM/component object;
  - action methods reference named specifications and do not construct selectors inline;
  - static fields must not retain session-bound Playwright/Appium locator or element instances.
- Use subject-aware test abstraction:
  - invoke a workflow/service method when a multi-step flow is a prerequisite or repeated setup for another subject;
  - keep page, component, API, messaging, repository, or controller actions explicit when that flow is the behavior under test;
  - preserve explicit actions and request review when the test subject is uncertain;
  - retain nested diagnostic reporting inside consolidated workflows.
- Require application abstractions to call TAF controllers rather than duplicating controller transport or lifecycle behavior.
- Allow Spring to compose factories, typed configuration, and stable dependencies. Mutable browser-, device-, API-, event-, and database-bound objects resolve resources from the active `TestSession` and must not capture invocation state in singleton beans.
- Preserve explicit TestNG and Cucumber lifecycle integrations defined by ADR-006, including TestNG XML orchestration and exactly-once Cucumber scenario lifecycle.
- Keep lifecycle contracts and implementations in framework runner modules:
  - `taf-runner-testng` provides `TafBaseTest`, TestNG lifecycle behavior, test-case resolution, and session access.
  - the Cucumber runner module provides `TafCucumberHooks`, the TAF runner based on `AbstractTestNGCucumberTests`, Cucumber-TestNG integration, and the reporting bridge.
- Require the consumer project only to extend and configure these framework integrations. Scaffolding must not copy or recreate `TafBaseTest`, `TafCucumberHooks`, `TestSessionLifecycle`, controller factories, or runner lifecycle code.
- Require framework base tests/hooks to own preflight invocation, test-case resolution, typed test-definition access, reporting begin/end, step initialization/flushing, failure-evidence coordination, and cleanup delegation. Consumer tests must not recreate these responsibilities in wrappers such as `executeReported()`.
- Prefer normal Spring injection for consumer pages, workflows, validators, and services. Generic `service(Class<T>)` lookup is not the default public consumer pattern because it hides dependencies and harms readability.
- Require concise framework conveniences such as typed test-input/expected-output access and readable step declaration so consumer tests do not need reporter or context plumbing.
- Require the official scaffolder to generate:
  1. a conventional `@SpringBootApplication` class;
  2. only the selected capability starters and runner modules;
  3. typed configuration templates for all named controller instances;
  4. base, local, and CI application profiles;
  5. environment-variable or secret references with explicit unresolved placeholders for required environment-specific values;
  6. aggregated preflight validation for unresolved configuration, missing secret references, dependency readiness, and incompatible capability selections;
  7. TestNG tests extending framework-owned `TafBaseTest`;
  8. when BDD is selected, a thin project runner extending the TAF Cucumber runner, TAF hook-glue registration, and a separate Cucumber TestNG XML suite;
  9. a standard package for customer `@Configuration`, `@ConfigurationProperties`, adapters, and third-party beans;
  10. architecture and Spring context-loading tests.
- Require generated examples to demonstrate both valid abstraction levels: a composite prerequisite workflow and explicit actions for functionality under test.
- Allow generated projects to compile with unresolved environment placeholders, but prohibit environment execution until preflight reports and rejects every unresolved required value. Never scaffold literal secrets.
- Require one authoritative base declaration for each required property. Profile configuration overrides it only when an environment-specific value is intentional.
- Permit customer-specific integrations through ordinary consumer Spring configuration and public extension points without requiring a Runtime change.
- Maintain three reference levels:
  - focused golden projects such as SauceDemo for Playwright;
  - cross-capability reference projects;
  - an eventual comprehensive reference implementation covering web, API, messaging, persistence, mobile, and AI workflows.
- Do not make the comprehensive reference implementation a prerequisite for early Runtime delivery.

## Consequences

- Human and agent authors receive consistent extension points and examples.
- Consumer projects are recognizable Spring Boot applications that Java developers can support and extend using standard conventions.
- Application-specific reusable abstractions remain possible without polluting TAF Runtime.
- Capability combinations can evolve without requiring unused packages in every project.
- Blueprints, schemas, scaffolding, documentation, and golden projects become versioned product assets that require maintenance.
- Package names may vary when declared structures preserve the approved dependency and responsibility boundaries.
- The scaffolder and starter portfolio become supported product surfaces with compatibility and documentation obligations.
- Semantic review remains necessary because static rules cannot reliably determine whether a flow is prerequisite or test subject.

## Alternatives considered

- Let each agent design the project — rejected because results are not repeatable and architectural intent is easily lost.
- Put every reusable abstraction in Runtime — rejected because application-specific pages, APIs, events, and queries do not belong in a general-purpose framework.
- Require one rigid package tree for all projects — rejected because single-capability and multi-capability projects have materially different needs.
- Use examples only — rejected because examples are guidance, not enforcement.
- Generate framework lifecycle classes into every consumer project — rejected because copies would drift, duplicate ownership, and bypass framework upgrades.
- Make plain Java the default consumer model — rejected because it increases custom bootstrap and integration work while reducing configuration consistency and supportability.
- Consolidate every multi-step flow — rejected because it hides the actions when that flow is the subject of testing.
- Require every test to expose all low-level actions — rejected because repeated prerequisites would obscure the behavior under test.

## Compliance and verification

- Official scaffolding shall create only approved blueprint versions and declared capability combinations.
- Scaffold verification shall prove that the generated Spring context loads under local and CI profiles, selected starters activate, and unselected capabilities remain absent.
- Configuration tests shall prove typed binding for every named controller instance and aggregated rejection of unresolved required values.
- Architecture tests shall enforce test-to-workflow-to-object-to-controller dependency direction.
- Object-design tests shall reject selector construction inside representative action methods and session-bound static locator instances; blueprint review shall verify locator ownership by POM/PCOM/screen objects.
- Consumer projects shall not import reporter-vendor APIs directly or reproduce Runtime controller, runner, or lifecycle implementations.
- Consumer-project tests shall not define reporter/preflight/session lifecycle wrappers or use generic service lookup where blueprint-provided injection applies.
- Runner tests shall prove framework-owned `TafBaseTest`/`TafCucumberHooks` usage and exactly one session per TestNG method or Cucumber scenario.
- Focused golden projects shall verify Spring Boot startup, starter selection, typed configuration, intentional profile overrides, required-value preflight, centralized locators, test data, traceability, lifecycle, reporting, sanitization, extension configuration, and representative execution.
- Golden projects shall include one test using a workflow as a prerequisite and another using explicit actions when that functionality is the test subject. Reports shall retain nested diagnostics for the composite workflow.
- Conformance automation shall not require workflow consolidation universally. Subject-aware abstraction is confirmed through requirement/test-condition traceability and agent or human review; uncertainty defaults to explicit steps.
- Cross-capability golden projects shall verify multiple named controllers of the same type, including per-service databases, without singleton session leakage.
- Blueprint changes that alter public extension points require compatibility review and migration guidance.

## Related documents

- Codinglair TAF and Quality Intelligence SAD v1.8
- ADR-003: Approve the TestController breaking redesign
- ADR-005: Keep reporting vendor-neutral and isolate Allure and AspectJ
- ADR-006: Keep TestNG and Cucumber test models separate while using TestNG XML orchestration
- ADR-010: Write generated code as proposed working-tree changes without autonomous commits
- ADR-018: Package optional controllers as capability-specific modules
- ADR-020: Enforce conformance for agent-generated and migrated test assets

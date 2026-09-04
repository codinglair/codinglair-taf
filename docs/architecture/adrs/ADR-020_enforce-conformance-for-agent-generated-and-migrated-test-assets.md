# ADR-020: Enforce conformance for agent-generated and migrated test assets

**Status:** Accepted  
**Date:** 2026-08-03  
**Last updated:** 2026-08-05  
**Decision owners:** Product Owner and Solution Architecture  
**Scope:** Coding agents, Quality Intelligence generation, migrations, consumer projects, and reference projects

## Context

Compilation and happy-path execution do not prove that a generated test project uses TAF correctly. An agent can produce executable code while removing page-object boundaries, configuration, data providers, test-case traceability, lifecycle integration, reporting, sanitization, or TestNG/Cucumber audience separation.

Prompts and written instructions alone cannot provide reliable architectural enforcement. The desired behavior must be expressed through blueprints, schemas, capability contracts, automated validation, and executable reference projects.

## Decision

- Treat agent-generated and migrated assets as proposed working-tree changes until applicable automated conformance gates pass and a human approves them.
- Use a schema-validated, versioned project descriptor to declare project type, Runtime version, capabilities, runners, test-definition provider, and reporting adapter. The descriptor contains no secrets and does not replace Spring environment configuration.
- Make Runtime/MCP capability manifests authoritative for supported controllers, lifecycle contracts, configuration, reporting, and test-definition features.
- Require generation to follow this controlled pipeline:
  1. requirements and source assessment;
  2. capability selection;
  3. approved blueprint selection;
  4. test-design proposal;
  5. proposed code changes;
  6. structural and dependency validation;
  7. compilation and verification;
  8. controlled smoke execution;
  9. reporting and traceability validation;
  10. human review.
- Require these applicable conformance gates:
  - blueprint and descriptor schema validation;
  - default Spring Boot application, selected-starter, typed-configuration, profile, extension-package, and context-loading validation;
  - architectural dependency and responsibility validation;
  - centralized locator ownership and prohibition of inline selector construction or session-bound static locator instances;
  - exclusive lifecycle ownership by `TafBaseTest` for ordinary TestNG methods and `TafCucumberHooks` for Cucumber scenarios, with listeners restricted to observation/reporting;
  - dependency and optional-module boundary validation;
  - externalized configuration, secret-reference, and sanitization validation;
  - unique test-case/test-definition/requirement traceability validation;
  - hierarchical, audience-appropriate, duplicate-suppressed reporting validation;
  - semantic review of whether prerequisite flows are appropriately consolidated and behavior under test remains explicit;
  - unit, integration, contract, architecture, and capability smoke verification.
- Require a source-to-target compatibility matrix for migrations. Demonstrated structure, configuration, data, traceability, lifecycle, reporting, and behavior may not be silently removed.
- Permit agents to generate application-specific POM, PCOM, AOM, screen, messaging, repository, service, model, workflow, and validation assets inside approved consumer extension points.
- Require generators to preserve explicit actions when the behavior under test is uncertain. They must not consolidate steps merely to reduce code size.
- Separate enforceable conformance from semantic review:
  - deterministic rules reject copied lifecycle code, consumer reporting wrappers, generic service lookup in blueprint examples, inline selectors, invalid dependency direction, duplicated configuration authority, and missing report/evidence contracts;
  - agent/human review determines whether a particular workflow is prerequisite or test subject using requirement and test-condition traceability.
- Prohibit agents working on consumer tests from autonomously changing Runtime contracts, introducing controllers, bypassing lifecycle/reporting/sanitization, adding unapproved repositories, or declaring their own output compliant.
- When a required capability is absent or an approved blueprint cannot express the design, require a blocked handoff or separately approved framework assignment.
- Implement complementary enforcement using ArchUnit, Maven Enforcer, module-boundary tests, schema validation, a TAF conformance validator, and golden-project CI suites. The validator shall be usable through Maven/CLI and may later be exposed as a coarse-grained MCP workflow.

## Consequences

- Compilation alone is no longer an acceptance criterion for generated or migrated projects.
- Architecture knowledge becomes versioned and executable rather than dependent on model memory or prompt quality.
- Generation requires additional validation time and maintenance of schemas, rules, and golden projects.
- Some readability decisions remain review decisions rather than static-analysis failures.
- Initial autonomy remains deliberately limited until repeated evaluations demonstrate acceptable conformance.
- Failures become actionable: consumer-code defects, framework capability gaps, blueprint gaps, and environment failures are reported separately.

## Alternatives considered

- Prompt-only enforcement — rejected because it is probabilistic and difficult to audit.
- Human review only — rejected because reviewers can miss structural and lifecycle violations and cannot scale safely.
- Compilation and test pass as the only gate — rejected because executable code can bypass essential framework features.
- Allow the agent to repair Runtime during consumer generation — rejected because it expands authority and obscures capability gaps.

## Compliance and verification

- Every generation or migration assignment shall map acceptance criteria to produced assets and verification evidence.
- CI shall run the conformance validator for official examples and generated-project evaluation fixtures.
- Negative fixtures shall prove that direct Allure usage, controller construction, hard-coded secrets/URLs, duplicate identifiers, listener-owned sessions, consumer reporting lifecycle wrappers, inline selectors, session-bound static locators, mixed runner lifecycles, and Runtime implementation copying are rejected.
- Scaffold fixtures shall prove that unresolved placeholders block environment execution with aggregated diagnostics, while safe generation and compilation remain possible before real environment values are supplied.
- Dependency fixtures shall prove that unselected capability starters and their vendor libraries are absent.
- Golden fixtures shall demonstrate both a composite prerequisite workflow with nested reporting and explicit actions for the same functionality when it is the test subject.
- Review evidence shall record the identified test subject and chosen abstraction level. The conformance validator shall not impose universal workflow consolidation.
- Golden projects shall fail when affected Runtime contracts break consumer architecture, reporting, lifecycle, or traceability.
- Conformance exceptions require an explicit architecture decision or assignment approval and must be recorded rather than silently waived.

## Related documents

- Codinglair TAF and Quality Intelligence SAD v1.8
- ADR-005: Keep reporting vendor-neutral and isolate Allure and AspectJ
- ADR-006: Keep TestNG and Cucumber test models separate while using TestNG XML orchestration
- ADR-009: Expose coarse-grained MCP workflows with asynchronous jobs
- ADR-010: Write generated code as proposed working-tree changes without autonomous commits
- ADR-011: Keep secrets outside model and MCP context
- ADR-014: Separate framework self-tests from consumer test execution and tier CI verification
- ADR-019: Standardize consumer-project blueprints and test-design patterns

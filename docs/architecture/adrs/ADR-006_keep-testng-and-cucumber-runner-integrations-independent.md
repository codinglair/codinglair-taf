# ADR-006: Keep TestNG and Cucumber test models separate while using TestNG XML orchestration

**Status:** Accepted  
**Date:** 2026-07-24  
**Last updated:** 2026-08-03  
**Decision owners:** Product Owner and Solution Architecture  
**Scope:** Codinglair TAF product family

## Context

Forcing every technical test through a BDD interface produces Gherkin scenarios and reports that are unreadable to business users. Technical boundary, malformed-input, integration, and data-driven tests have a different audience from executable acceptance specifications.

The legacy framework deliberately kept the TestNG and Cucumber authoring abstractions separate while allowing the Cucumber runner abstraction to extend Cucumber's official `AbstractTestNGCucumberTests` adapter. This allowed Cucumber suites to be organized through TestNG XML without requiring ordinary TestNG tests to use Cucumber, Gherkin, or a BDD base class.

TestNG XML suite orchestration is an intentional compatibility and usability requirement. Separate XML suite files can select and organize Cucumber runners and can be supplied dynamically by local execution or CI/CD pipelines. Adding a new Cucumber suite must not normally require editing Maven build configuration.

## Decision

- Keep TestNG technical-test abstractions and Cucumber BDD authoring abstractions separate over shared Runtime services.
- Use TestNG as the supported orchestration engine for both ordinary TestNG tests and Cucumber runner classes.
- The framework Cucumber runner abstraction shall extend Cucumber's official `AbstractTestNGCucumberTests` adapter and use the `cucumber-testng` integration.
- Support organization and selection of Cucumber runner classes through one or more TestNG XML suite files.
- Allow TestNG XML suite paths to be supplied at execution time, including from CI/CD pipelines, without requiring a Maven POM change for every new Cucumber suite.
- Do not require ordinary TestNG tests to inherit from a Cucumber runner, use Cucumber hooks, provide Gherkin scenarios, or enter the Cucumber reporting pipeline.
- Shared inheritance between technical TestNG test abstractions and Cucumber BDD abstractions is prohibited; inheritance from the official Cucumber TestNG execution adapter is explicitly permitted and required for the supported orchestration model.
- Use Cucumber for curated business-facing behavior based on clear acceptance criteria.
- Use TestNG for technical, boundary, protocol, integration, resilience, negative, and data-driven verification.
- Allow negative business outcomes in Cucumber when they are part of the behavioral contract.
- Keep Cucumber business reports and technical TestNG reports logically separate while contributing to unified traceability.
- Make `TafBaseTest` the sole lifecycle owner for ordinary TestNG test methods. Its `@BeforeMethod` creates and binds one invocation-specific `TestSession`; its `@AfterMethod(alwaysRun = true)` closes and unbinds it.
- Make `TafCucumberHooks` the sole lifecycle owner for Cucumber scenarios, including scenarios orchestrated through `AbstractTestNGCucumberTests` and TestNG XML. Its Cucumber `@Before` and `@After` hooks create and close the scenario session.
- Allow both lifecycle owners to delegate common creation, outcome recording, evidence finalization, and cleanup logic to one Spring-managed `TestSessionLifecycle` service. The shared service is implementation reuse, not an additional lifecycle trigger and not a shared `TestSession` instance.
- Inject stable factories and lifecycle services through Spring. Do not register mutable `TestSession` as a singleton Spring bean. A current-session proxy or accessor may resolve an already-bound session but must not create one.
- Restrict TestNG, Cucumber reporting, and provider-specific reporting listeners to observation, outcome translation, and sanitized reporting. They must never create, bind, close, or own `TestSession` instances.
- Do not support listener-based session lifecycle as an alternative in the approved baseline. A future listener lifecycle adapter would require a separate decision, mutually exclusive activation, and duplicate-session conformance tests.
- Ensure the TestNG-visible invocations used to orchestrate `AbstractTestNGCucumberTests` do not create a session outside `TafCucumberHooks`.
- Prevent duplicate cleanup, test events, reporting steps, results, and evidence across runner, hook, listener, plugin, and reporter integrations.
- Keep reporting provider-neutral. Allure-specific TestNG and Cucumber behavior remains confined to the Allure adapter boundary.

## Consequences

- Business reports remain understandable.
- Technical coverage is not constrained by Gherkin.
- Teams retain TestNG XML as a single, familiar suite-orchestration mechanism.
- CI/CD pipelines can select separate technical and BDD XML suites through execution parameters rather than build-file modifications.
- The Cucumber runner integration has a deliberate dependency on `cucumber-testng`; this does not create a dependency from ordinary TestNG tests to Cucumber.
- Native Cucumber execution without TestNG is not required by this decision and may be introduced later only as an additional adapter without removing TestNG XML support.
- TestNG/Cucumber lifecycle and reporting interoperability require explicit conformance tests.
- Lifecycle ownership is explicit and discoverable: base test for ordinary TestNG methods, hooks for Cucumber scenarios, and listeners for observation/reporting only.
- A combined Allure execution may contain both result types when intentionally configured. When physically separate Allure reports are required, technical and BDD XML suites should run in separate executions or forks with distinct result directories. Dedicated Cucumber business reports must contain only Cucumber scenarios.

## Alternatives considered

- BDD for every test — rejected because it pollutes specifications and reports.
- TestNG only — rejected because it removes executable business specifications.
- Native-only Cucumber execution with no TestNG adapter — rejected because it removes the intentional TestNG XML orchestration capability and shifts suite management into code, Cucumber-specific configuration, or repeated Maven configuration changes.
- A deferred optional compatibility bridge — rejected as the sole solution because TestNG XML orchestration is a required framework capability, not merely a legacy migration convenience.
- One shared framework base class for technical and BDD tests — rejected because it couples technical tests to BDD concerns and can pollute business-facing reporting.

## Compliance and verification

- Architecture and dependency tests shall verify that ordinary TestNG test abstractions do not depend on Cucumber while the Cucumber TestNG runner integration declares the approved `cucumber-testng` dependency.
- A framework integration test shall execute a sample Cucumber runner through a TestNG XML suite.
- A suite-selection test shall demonstrate that different TestNG XML files can select different Cucumber suites without modifying the Maven POM.
- Reporting tests shall demonstrate that ordinary TestNG tests do not appear in dedicated Cucumber business reports.
- Lifecycle tests shall verify exactly-once TestSession initialization and cleanup and prevent duplicate reporting events when TestNG and Cucumber integrations are combined.
- Lifecycle tests shall prove that `TafBaseTest` creates one session for each ordinary TestNG method, `TafCucumberHooks` creates one session for each Cucumber scenario, and TestNG-visible Cucumber runner invocations create no additional session.
- Listener tests shall prove that configured TestNG and reporting listeners cannot create, bind, close, or replace the current session.
- Parallel execution tests shall prove that invocation-specific sessions remain isolated and that a session-aware proxy only resolves an already-bound session.
- CI/CD reference configuration shall demonstrate passing a TestNG XML suite path as an execution parameter.
- The applicable implementation assignments shall include unit, integration, contract, security, reporting, lifecycle, and compatibility tests.
- Public contract or schema changes shall update the Legacy Contract Compatibility Matrix and migration guidance.
- Deviations require a superseding ADR or an explicitly approved amendment.

## Revision note

The 2026-08-01 amendment clarified that "independent" refers to test authoring abstractions, audience, and reporting boundaries and specifically retained TestNG XML orchestration. The 2026-08-03 amendment establishes exclusive lifecycle ownership: `TafBaseTest` for ordinary TestNG methods and `TafCucumberHooks` for Cucumber scenarios. Listeners are observation/reporting integrations only and may not create or close sessions. Any earlier interpretation permitting simultaneous base-class/hook and listener session initialization is superseded.

## Related documents

- Test_Automation_Quality_Intelligence_BRD_v1.1
- Codinglair_TAF_Quality_Intelligence_SAD_v1.8
- ADR-019: Standardize consumer-project blueprints and test-design patterns
- ADR-020: Enforce conformance for agent-generated and migrated test assets
- Software_Engineering_Plan_Codinglair_TAF
- Legacy Contract Compatibility Matrix, when created

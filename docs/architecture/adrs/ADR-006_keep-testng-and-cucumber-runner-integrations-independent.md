# ADR-006: Keep TestNG and Cucumber Test Models Separate while Using TestNG XML Orchestration

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

**Context** Forcing every technical test through a BDD interface produces Gherkin scenarios and reports that are unreadable to business users. The legacy framework intentionally kept authoring models separate while extending Cucumber's official TestNG adapter so Cucumber suites could be organized through TestNG XML.

**Decision**

- Keep ordinary TestNG technical-test abstractions and Cucumber BDD authoring abstractions separate over shared Runtime services.
- Use TestNG as the supported orchestration engine for both ordinary TestNG classes and Cucumber runner classes.
- Require the framework Cucumber runner abstraction to extend `AbstractTestNGCucumberTests` through `cucumber-testng`.
- Support separate TestNG XML suite files selectable at execution time and from CI/CD parameters without a Maven POM change for every new Cucumber suite.
- Do not require ordinary TestNG tests to inherit from Cucumber, use Gherkin, execute Cucumber hooks, or enter dedicated Cucumber reports.
- Use Cucumber for curated business-facing behavior based on clear acceptance criteria.
- Use TestNG for technical, boundary, protocol, integration, resilience, negative, and data-driven verification.
- Allow negative business outcomes in Cucumber when they are part of the behavioral contract.
- Keep dedicated Cucumber business reports free of ordinary TestNG tests while contributing to unified traceability.
- Make `TafBaseTest` the exclusive session lifecycle owner for ordinary TestNG methods and `TafCucumberHooks` the exclusive owner for Cucumber scenarios.
- Permit both adapters to delegate to the same Spring-managed `TestSessionLifecycle` implementation while creating distinct invocation-specific `TestSession` instances.
- Restrict TestNG and reporting listeners to observation/reporting; they must never create, bind, or close a `TestSession`.
- Prevent duplicate reporting steps, results, and evidence when TestNG listeners and Cucumber hooks/plugins are combined.

**Consequences**

- Business reports remain understandable.
- Technical coverage is not constrained by Gherkin.
- Teams retain parameterized TestNG XML suite orchestration for local and CI/CD execution.
- Cucumber has an intentional `cucumber-testng` dependency without coupling ordinary TestNG tests to BDD.
- Lifecycle and reporting interoperability require explicit conformance tests.

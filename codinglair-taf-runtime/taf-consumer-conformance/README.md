# TAF Consumer Conformance

Reusable blueprint 1.0 validation for generated, migrated, and golden consumer projects. The main
artifact contains the validator, CLI, result contracts, and authoritative schema resource. Its own
fixture tests remain ordinary module test classes and are not published in the main artifact.

Consumer build tests can call:

```java
new ConsumerProjectValidator().validate(projectRoot).throwIfInvalid();
```

The CLI accepts one unpacked consumer-project directory and exits `2` for conformance failures.
Report producers can additionally pass their sanitized neutral JSON inspection output to
`validateReportOutput`; it requires unique event IDs and at least `TEST` and `VALIDATION` levels.

The validator performs structural checks and does not execute a target environment. Consumer builds
remain responsible for their Spring `ApplicationContextRunner` profile tests, `ConsumerPreflight`
execution, runner lifecycle integration tests, and capability smoke tests. The structural gate proves
that those assets exist and reject prohibited ownership patterns; it does not replace them.

SAD v1.8 source checks use the Java 25 compiler tree API rather than matching Java text. They reject
inline selector construction in declared page/component/screen owners, session-bound static fields,
misplaced `LocatorSpec` declarations, consumer lifecycle/reporting wrappers, manual lifecycle calls,
generic service lookup, direct controller/reporter construction, Allure imports, copied lifecycle
implementations, and prohibited package directions. Resource checks cover the Spring entry point,
typed configuration, profiles, suites, context tests, test data, property authority, and resolvable
TestNG/Cucumber identifiers. Diagnostics retain the offending file and line in the explanation.

The gate deliberately does not require a workflow/service layer and does not reject explicit page,
API, or controller actions. Whether a flow is prerequisite setup or the behavior under test remains a
required semantic-review decision recorded with the checklist.

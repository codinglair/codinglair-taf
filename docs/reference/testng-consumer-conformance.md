# TestNG consumer conformance

Governed Codinglair TAF consumer builds use TestNG for ordinary tests and Cucumber through the
framework TestNG runner. Framework implementation tests in the TAF reactor may continue to use
JUnit when they do not represent a consumer lifecycle.

## Required Maven binding

Consumer POMs declare `surefire-testng` explicitly as a dependency of
`maven-surefire-plugin` and bind both goals of
`com.codinglair.taf:taf-consumer-conformance-maven-plugin`:

```xml
<execution>
  <id>validate-taf-consumer</id>
  <goals>
    <goal>validate</goal>
    <goal>validate-compiled-tests</goal>
  </goals>
</execution>
```

The `validate` goal inspects the effective Maven model and resolved test dependencies. It rejects a
missing explicit TestNG provider, missing TAF runner integration, JUnit artifacts, mixed engines,
and missing or obsolete governed descriptors.

The `validate-compiled-tests` goal runs in `process-test-classes`, after `test-compile` and before
Surefire or Failsafe. It inspects actual compiled classes, annotations, inherited base types, and
lifecycle declarations. For Cucumber runners it also verifies that the compiled `CucumberOptions`
registers `com.codinglair.taf.runtime.cucumber`, so removing the framework hook glue fails before a
scenario body can execute. This scheduling intentionally clarifies the SAD's general “validate before
tests” wording: a clean Maven `validate` has no bytecode to inspect, and the build never starts a
hidden recursive compilation. Both controls still fail before any consumer test body executes.

## Lifecycle ownership checklist

- Ordinary TestNG classes extend framework `TafBaseTest`.
- Cucumber runners extend framework `AbstractCucumberRunner` and use separate TestNG XML suite
  selection.
- Cucumber glue includes `com.codinglair.taf.runtime.cucumber` so `TafCucumberHooks` remains the
  scenario lifecycle owner.
- Consumer code does not declare TestNG method lifecycle or Cucumber before/after hooks that copy
  session ownership.
- JUnit XML is permitted as report output; JUnit APIs and engines are not executable consumer
  runners.

## Descriptor migration

Schema `1.2` is the governed-runner descriptor. Projects on `1.0` or `1.1` retain their project,
runtime, capability, TestNG runner, test-definition, reporting, standalone build-root, and external
SUT fields, change `schemaVersion` to `1.2`, and add the mandatory plugin/provider binding above.
The validator reports this path explicitly. A missing descriptor is an error and never disables
provider validation.

An exceptional local diagnostic run may set `-Dtaf.conformance.skip=true`; this emits a warning and
must not be used in CI, release, or claimed governed-consumer verification.

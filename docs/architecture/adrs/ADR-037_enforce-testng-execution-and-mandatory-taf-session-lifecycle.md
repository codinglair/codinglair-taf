# ADR-037: Enforce TestNG Execution and Mandatory TAF Session Lifecycle

- **Status:** Accepted
- **Date:** 2026-10-18
- **Decision owners:** Codinglair TAF Architecture
- **Applies to:** TAF runners, generated consumers, capability entry points and TAF 1.3.2+

## Context

A consumer changed the test runtime to JUnit. Tests executed and passed while bypassing TAF's TestNG-owned `TestSession` lifecycle. The run therefore did not provide the governed session, cleanup, reporting and evidence guarantees expected of TAF.

Runtime-only detection cannot identify an unsupported test that never invokes TAF code. Conversely, build-only validation cannot protect capability calls made outside an active invocation. Both controls are required.

## Decision

TestNG is the sole supported ordinary test runner for this architecture revision. Cucumber remains supported through the approved TestNG orchestration and TAF Cucumber hooks.
TestNG is the mandatory runner for TAF consumer functional/E2E execution and released-artifact consumer conformance. TAF implementation-level unit, component, and integration tests may use JUnit when they do not represent or invoke the supported consumer lifecycle.

Official consumer builds shall validate before test execution that:

- the selected Surefire provider is TestNG;
- the required TAF TestNG integration is registered;
- test classes use supported TestNG or approved Cucumber annotations;
- JUnit engines or mixed discovery capable of bypassing TAF lifecycle ownership are absent;
- `TafBaseTest` and `TafCucumberHooks` remain the exclusive lifecycle owners defined by the SAD.

Every TAF capability entry point shall require an already active, invocation-bound `TestSession`. When none exists, it fails immediately with stable error `TAF-LIFECYCLE-001` or its final approved equivalent. TAF shall not create an implicit session, execute an ungoverned operation or downgrade the violation to a warning.

Supporting another runner in the future requires a first-class adapter providing equivalent session ownership, cleanup, reporting, evidence, parallel isolation and negative conformance.

## Consequences

### Positive

- Unsupported runner substitution cannot silently produce a false successful TAF run.
- Capability access outside the lifecycle fails deterministically.
- TestNG compatibility becomes explicit and testable.
- Cleanup, reporting and evidence retain one lifecycle owner.

### Negative

- Consumers cannot use JUnit for TAF tests in the current release line.
- Projects using JUnit for SUT unit tests must keep those tests in the separate SUT build or another independently configured build.
- Provider and compiled-test inspection adds a validation step.

## Alternatives Considered

### Permit any runner and document hooks

Rejected because the lifecycle was already bypassed without failure.

### Validate only when a capability is invoked

Rejected because a misconfigured or empty test can still pass without invoking TAF.

### Create an implicit session

Rejected because it hides incorrect configuration and cannot reliably reproduce governed setup, evidence and cleanup.

## Verification

- A valid TestNG consumer creates and closes exactly one session per invocation.
- Cucumber through TestNG creates and closes exactly one session per scenario.
- JUnit-only, mixed-provider and missing-integration consumers fail before execution.
- Each capability fails with the lifecycle error when invoked without an active session.
- Parallel TestNG invocations preserve independent sessions and evidence ownership.


# ADR-014: Separate framework self-tests from consumer test execution and tier CI verification

**Status:** Accepted  
**Date:** 2026-07-24  
**Decision owners:** Product Owner and Solution Architecture  
**Scope:** Codinglair TAF product family

## Context

Framework modules require unit, integration, contract, browser, messaging, database, container, and Appium tests. These tests must protect framework releases without executing when consumers run their own test suites. Running every heavy matrix on every PR would also slow feedback.

## Decision

- Place internal tests in each framework module's test source sets.
- Run all unit tests on every PR.
- Run affected integration/contract tests and cross-module vertical smoke tests on PR.
- Run relevant browser/Appium smoke when those modules change.
- Run full browser, database, messaging, Testcontainers, Appium, compatibility, security, load, cleanup, and Kind suites nightly and before release.
- Block release unless the complete required verification passes.
- Publish consumer test utilities only through a deliberate taf-test-support module.

## Consequences

- Framework quality is verified during its own build.
- Consumers do not inherit internal tests or fixtures.
- PR feedback remains practical while full coverage is retained.
- CI change-impact rules and authoritative release gates must be maintained.

## Alternatives considered

- Run internal tests in consumer projects — rejected because it is irrelevant and surprising.
- Run every heavy test on every PR — rejected due to feedback time and scarce device resources.
- Nightly-only integration tests — rejected because affected integrations need PR smoke coverage.

## Compliance and verification

- Architecture and dependency tests shall enforce machine-verifiable boundaries.
- The applicable implementation assignments shall include unit, integration, contract, security, and compatibility tests.
- Public contract or schema changes shall update the Legacy Contract Compatibility Matrix and migration guidance.
- Deviations require a superseding ADR or an explicitly approved amendment.

## Related documents

- Test Automation Framework and Quality Intelligence Platform BRD v1.0
- Codinglair TAF and Quality Intelligence SAD v1.0
- Codinglair TAF Engineering and DevOps Implementation Plan v1.0
- Legacy Contract Compatibility Matrix, when created

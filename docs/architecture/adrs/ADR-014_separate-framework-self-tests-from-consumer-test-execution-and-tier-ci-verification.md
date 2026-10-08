# ADR-014: Separate Framework Self-Tests from Consumer Test Execution and Tier CI Verification

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

**Context** Framework modules require unit, integration, contract, browser, messaging, database, container, and Appium tests. These tests must protect framework releases without executing when consumers run their own test suites. Running every heavy matrix on every PR would also slow feedback.

**Decision**

- Place internal tests in each framework module's test source sets.
- Run all unit tests on every PR.
- Run affected integration/contract tests and cross-module vertical smoke tests on PR.
- Run relevant browser/Appium smoke when those modules change.
- Run full browser, database, messaging, Testcontainers, Appium, compatibility, security, load, cleanup, and Kind suites nightly and before release.
- Block release unless the complete required verification passes.
- Publish consumer test utilities only through a deliberate taf-test-support module.

**Consequences**

- Framework quality is verified during its own build.
- Consumers do not inherit internal tests or fixtures.
- PR feedback remains practical while full coverage is retained.
- CI change-impact rules and authoritative release gates must be maintained.

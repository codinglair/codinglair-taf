# Nightly and Release Verification

The authoritative required-suite graph is
`.github/workflows/verification-matrix.yml`. Both `nightly.yml` and
`release.yml` call that same workflow, so the manually authorized release gate
cannot select a smaller matrix than the scheduled gate.

## Declared compatibility and suite mapping

| Declaration | Required verification |
|---|---|
| Java 25, Spring Boot 4.1.x, Spring AI 2.0.1 | Full Runtime reactor; dependency, architecture, API, and schema compatibility profiles; complete MCP reactor |
| Linux CI and Maven Wrapper | Every lane uses Ubuntu 24.04, Temurin 25, and `./mvnw` |
| Playwright 1.57.0 | The authoritative browser smoke's Chromium, Firefox, and WebKit engine matrix |
| Testcontainers 2.0.5 | Environment, MongoDB definitions, PostgreSQL/Mongo migration, Kafka, RabbitMQ, JMS, and WireMock suites |
| Appium / Android UiAutomator2 | Approved MOB-003 API 34 emulator recipe, two clean smokes, controlled-failure cleanup |
| MCP STDIO/HTTP, security, cancellation | Complete MCP `mcp-e2e,security-it` reactor |

Windows 11 remains a supported development environment and is covered by the
Java 25 PR/dependency evidence recorded in the compatibility matrix. The
release matrix verifies the distributable CI target on Linux. Spring Boot 4.2.x
is not declared supported and remains a future compatibility candidate.

## Resource limits

The graph is suitable for a 32 GB self-hosted runner pool and hosted runners:

- the browser engine matrix runs in one bounded job and the container matrix uses `max-parallel: 2` with `fail-fast: false`;
- each matrix child receives an isolated hosted runner when GitHub-hosted;
- the resource-heavy Android emulator workflow has a fixed concurrency group,
  does not cancel an in-progress qualification, and never runs a physical device;
- all jobs have explicit timeouts; no unbounded retry or fork is enabled.

If a self-hosted deployment assigns multiple jobs to one 32 GB host, its runner
group must limit the host to two concurrent ordinary jobs or one emulator job.

## Rerun and quarantine policy

Required suites are never quarantined out of release verification. A failure is
triaged from its original artifact before an authorized operator may use
GitHub's whole-workflow rerun. There is no automatic test or job retry. Artifact
names contain both `github.run_id` and `github.run_attempt`; all-attempt reports
are uploaded with `always()` for 30 days, so a later green attempt cannot replace
the first failure. Repeatedly flaky tests remain release-blocking until fixed.
Any proposed quarantine requires maintainer approval, an owner, an expiry, a linked defect, and an
equivalent deterministic release-blocking check.

## Release-blocking summary

The stable `Release verification gate` runs with `always()` after Runtime and
architecture, all three browsers, all database/messaging/container suites, MCP
security/e2e, and Android emulator qualification. It accepts only `success` for
every required result; failure, cancellation, skip, empty, or unknown results
fail the workflow. Container lanes compare pre/post container and network IDs.
The emulator qualification also exercises controlled failure and teardown.

Physical-device tests use the `android-device` Maven profile only after separate
human authorization and device/cloud credentials are provisioned. They are not
part of automatic nightly or release execution and no workflow secret is added
by DEVOPS-002.

## Operator validation

Before accepting a release candidate:

1. run the repository workflow syntax and structural contract checks;
2. dispatch `Nightly Verification` and retain its complete successful attempt;
3. dispatch `Release Verification` on the reviewed candidate ref;
4. require the exact `Release verification gate` result and retain every attempt;
5. do not publish, tag, or deploy from these verification-only workflows.

Capacity and concurrency claims require a repeatable public load test. The current workflow bounds
CI concurrency but does not establish a general workload-capacity guarantee.

# Java, Spring, and ecosystem compatibility

This matrix records the public compatibility baseline declared by the current repository. The root
Maven POM and focused verification suites are authoritative when this summary differs from source.
The first public release establishes the baseline; no earlier public Codinglair TAF release contract
exists.

## Required platform

| Component | Baseline | Scope |
| --- | --- | --- |
| JDK | Java 25 | Compilation, tests, and released bytecode |
| Maven | Checked-in Maven Wrapper | Supported build entry point |
| Spring Boot | 4.1.0 BOM | Runtime and transport composition |
| Spring AI | 2.0.1 BOM | MCP transport layer only; not a Runtime dependency |
| CI operating system | Ubuntu 24.04 | Release verification target |
| Development operating system | Windows 11 or supported Linux | Maven Wrapper development and focused verification |

## Managed integration baselines

These versions are pinned by the root reactor. A capability is optional unless its artifact is
selected by a consumer.

| Integration | Version | Boundary |
| --- | --- | --- |
| TestNG | 7.12.0 | Independent TestNG runner adapter |
| Cucumber | 7.17.0 | Independent Cucumber runner adapter |
| Allure | 2.29.0 | Optional reporting adapter |
| AspectJ Weaver | 1.9.21.2 | Isolated to reporting integration; not a Runtime Core API |
| Playwright | 1.57.0 | Optional browser capability |
| Appium Java client | 10.1.1 | Optional Android capability |
| Testcontainers | 2.0.5 | Optional managed-environment providers |
| Jackson | 2.19.2 | Managed Runtime serialization baseline |

Provider modules also manage the database, messaging, SOAP, contract-validation, virtualization,
and observability libraries required by their own capabilities. The
[release capability matrix](../quick-start-capability-matrix.md) maps those artifacts to focused
verification and known limitations.

## Compatibility rules

- Runtime Core remains independently usable without MCP, Spring AI, provider adapters, or the
  proprietary-boundary module.
- TestNG and Cucumber share lifecycle contracts but do not depend on one another.
- Provider-neutral contracts do not depend on their technology adapters.
- STDIO and Streamable HTTP implement the same versioned MCP application contracts.
- Optional container, browser, broker, database, and Android checks require the prerequisites
  documented for those profiles; their absence does not make the base Runtime incompatible.
- Public API and schema changes are checked by the `api-compatibility` and
  `schema-compatibility` verification profiles. During the first public release these checks
  establish the baseline used by later releases.

## Verification

Use the narrowest relevant module test while developing. Maintainers use these focused profiles to
verify the declared boundaries:

```text
./mvnw -Pdocs verify
./mvnw -Papi-compatibility,schema-compatibility verify
./mvnw -Pdependency-analysis,architecture verify
```

Environment-dependent suites and the release matrix are described in
[nightly and release verification](../operations/nightly-and-release-verification.md). Installation
requirements and opt-in prerequisites are in
[installation and prerequisites](../reference/installation-and-prerequisites.md).

## Related architecture decisions

- [ADR-002: Spring Boot composition with a lightweight core](../architecture/adrs/ADR-002_use-spring-boot-composition-with-a-lightweight-framework-core.md)
- [ADR-006: independent TestNG and Cucumber integrations](../architecture/adrs/ADR-006_keep-testng-and-cucumber-runner-integrations-independent.md)
- [ADR-014: framework, consumer, and CI verification separation](../architecture/adrs/ADR-014_separate-framework-self-tests-from-consumer-test-execution-and-tier-ci-verification.md)
- [ADR-017: Java 25 platform baseline](../architecture/adrs/ADR-017_java25-platform-baseline.md)
- [Public architecture](../architecture/solution-architecture.md)

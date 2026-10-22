# Changelog

All notable user-visible changes to Codinglair TAF will be documented in this file. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project intends to use [Semantic Versioning](https://semver.org/spec/v2.0.0.html) for published releases.

## 1.3.2

- Enforced independent automation build roots for official scaffolding, including canonical-path,
  traversal/link, SUT-root, protected-file, dependency-graph, reactor and packaged-content checks.
  Same-repository layouts remain supported when the automation build and process stay separate.
- Made TestNG the governed consumer runner, retained Cucumber through its TestNG orchestration and
  framework hooks, and added pre-execution rejection of JUnit-only/mixed/missing-integration
  consumers. Capability use without the active invocation-owned session now fails with
  `TAF-LIFECYCLE-001`; TAF implementation tests may continue to use JUnit.
- Restored bounded, sanitized REST request, response, timing, and transport-failure evidence and
  associated optional Allure attachments with their owning method/route operation step.
- Converged published Jackson 2 runtime and data-format surfaces on 2.21.7 LTS, Jackson 3 runtime
  and YAML surfaces on 3.1.7 LTS, and shared annotations on 2.21; removed MCP-local annotations
  overrides and made the constraints explicit in the public BOM.
- Added exhaustive staged-SBOM Jackson scanning, bundled-class checks and HIGH/CRITICAL coverage
  for container and MCP image vulnerability gates. Consumers require no Jackson exclusions or
  mapper replacement.
- Aligned Cucumber modules on 7.34.9 to remove its relocated Jackson 2.17.0 runtime from container
  images, and refreshed the pinned Java 25/Noble Temurin images for patched OpenSSL packages.
- Added an expiring, exact-scope MCP STDIO disposition for two genuine HttpCore 5.3.6 findings
  embedded in the required Testcontainers Docker transport. Raw Trivy/SBOM evidence remains
  unsuppressed; the gate reports accepted findings separately and still rejects every unapproved
  HIGH/CRITICAL finding.
- Narrowed serializer ownership: JSON/YAML implementations moved behind module-local,
  Jackson-neutral public contracts; the explicitly enumerated Jackson 2 CSV, Mongo, migration,
  AWS, conformance and MCP remainder is owned by SER-133-001. Existing serialized formats and the
  legacy CSV facade remain compatible; consumers need no exclusions or mapper overrides.
- Qualified 18 isolated staged-artifact consumers (61 tests), external Boot 3.5.7 and Boot 4.0.1
  SUT processes, 26 standalone/runner safety scenarios, and separate TestNG, Cucumber and no-Allure
  evidence consumers. Hosted CI reruns, manual Allure readability, full browser/device and external
  broker qualification remain owner-managed and are not claimed here.

### Known Security Issues

- **Testcontainers / docker-java Zerodep:** The upstream `docker-java-transport-zerodep:3.7.1` dependency contains bundled components associated with reported security vulnerabilities. Upstream remediation is not yet available.
- Codinglair TAF currently suppresses the identified Trivy findings in its own security scans. These suppressions do not apply automatically to downstream consumers.
- Consumer security scans may independently report these vulnerabilities when the affected dependency is present. Consumers should evaluate the findings against their execution environment and security policies.
- This issue remains open pending an upstream fix or an approved alternative mitigation.

## [1.3.1] - 2026-10-13

### Fixed

- Restored the production MCP tool catalog for both STDIO and Streamable HTTP by composing the
  shared production services in each transport and failing startup when the expected seven-tool
  registration is incomplete or duplicated.
- Connected the supported MCP container launchers and Kind deployment to bounded writable
  workspace and state locations required by production composition while preserving non-root,
  read-only-root operation.

### Changed

- Expanded production-process, built-image, and authenticated Kind qualification to assert the
  exact MCP tool catalog, including discovery before and after pod replacement.
- Updated container CI path coverage for shared MCP composition changes and made MCP image release
  versioning derive from and validate against the Maven revision instead of a hardcoded tag.

## [1.3.0] - 2026-10-08

### Added

- First-class Apple Appium/XCUITest support in the existing mobile module and starter, including iPhone/iPad selection, native, hybrid, and Safari modes, local/remote/provider-compatible endpoints, topology-aware preflight, conditional evidence, MCP discovery, and generated consumer projects.
- A hosted macOS simulator qualification lane for the selected iPhone 16 / iOS 18.5 / Xcode 16.4 tuple, with native, hybrid, Safari, evidence, controlled-failure, and owned-cleanup checks.

### Changed

- The centrally managed Spring Boot BOM is upgraded from 4.1.0 to 4.1.1, aligning all Spring Framework modules on 7.0.9 without an individual `spring-webmvc` override.
- Mobile blueprints now accept explicit iOS/iPadOS selections while preserving the legacy Android default and the existing starter/module dependency graph.
- Apple configuration and operations use typed, named, session-owned controllers and the shared lifecycle, reporting, redaction, artifact, readiness, and environment-provider contracts.

### Security

- The Spring Boot 4.1.1 dependency refresh replaces the Spring Framework 7.0.8 artifacts reported in the control-plane vulnerability scan. The rebuilt image SBOM contains the aligned 7.0.9 Framework modules, and the existing Critical-severity Trivy gate passes without suppression.
- Apple endpoints, application resources, downloads, redirects, authentication, and evidence are governed by resource authorization, destination/trust checks, secret references, bounded transport, and shared redaction. Resolved credentials never enter configuration or MCP payloads.

### Known limitations

- The passing hosted qualification covers one local-host iPhone simulator tuple. It does not certify physical devices, iPad hardware, remote customer hosts, named cloud providers, or other Xcode/iOS combinations; logs and video remain conditional artifacts.

## [`1.2.0`] - 2026-09-20

### Added

- Capability-oriented Web, API, Database, Messaging, Mobile, Kafka, RabbitMQ, JMS, and AWS
  starters, all aligned by the existing consumer BOM.
- Deterministic Maven project generation for supported multi-capability selections, including
  generated-project preflight and collision-safe publication.
- One official MCP Server image with client-spawned STDIO and secured Streamable HTTP profiles,
  plus the supported Kind reference deployment.
- External staged-artifact conformance for every starter, the maximal starter/provider graph,
  generated projects, retained direct-module consumers, image profiles, and Kubernetes restart.

### Changed

- Starters are now the recommended consumer entry point; supported individual Runtime modules
  remain available for advanced consumers and no existing public coordinate was removed.
- Release verification now requires clean external consumers and generated projects that do not
  inherit Codinglair build configuration or rely on reactor-only resolution.
- Consumer and operator documentation now covers Maven and Gradle dependency syntax, starter
  activation, MCP image profiles, immutable image selection, and Kubernetes deployment.

### Security

- Starter secret-provider selection fails closed, and the local provider requires explicit
  activation rather than acting as a production fallback.
- The MCP image runs non-root with a read-only-root-compatible layout and retains digest-bound
  SBOM, provenance, signature, and vulnerability evidence during authorized publication.

### Known limitations

- Release 1.2.0 generates Maven projects only. Gradle Groovy and Kotlin examples cover dependency
  consumption syntax, not blueprint generation or Maven-plugin equivalence.
- The supported Kubernetes profile is single-replica and process-local; pod replacement proves
  service recovery, not persistence of jobs, approvals, audit history, or results.
- Android/UiAutomator2 is the supported generated Mobile baseline. iOS/XCUITest generation is not
  implemented in this release.

## `1.1.0` - 2026-09-13

### Added

- Optional `taf-messaging-aws` capability with independently usable EventBridge and SQS
  controllers, bounded polling, explicit acknowledgment and visibility control, route-to-SQS
  verification, sanitized evidence, and Spring Boot auto-configuration.
- Testcontainers-managed and declared-external LocalStack environment modes with immutable
  ownership manifests, dependency-ordered cleanup, parallel isolation, and preservation of
  operator-owned resources.
- Standalone staged-artifact AWS consumer smoke, LocalStack qualification, MCP contract/leak gates,
  immutable CI image qualification, and release supply-chain evidence.

### Changed

- The consumer BOM now includes the AWS messaging capability while preserving Runtime independence
  from MCP and keeping AWS SDK types outside Runtime Core contracts.
- Public documentation now covers AWS configuration, isolation, ownership, emulator deviations,
  authorized-AWS boundaries, CI operations, and upgrade guidance.

### Security

- AWS credentials remain opaque references; LocalStack placeholders are emulator-only. Receipt
  handles, credentials, authorization values, and seeded canaries are excluded from durable Runtime,
  MCP, and CI evidence.
- External mode is non-mutating by default, and unsafe shared-queue correlation scanning fails
  preflight.

### Known limitations

- LocalStack qualification does not establish IAM, service-quota, throttling, latency, or regional
  behavior on AWS. Authorized-AWS qualification requires a protected, explicitly approved
  environment and must receive a separate release disposition.
- LocalStack 4.14.0 accepts some malformed EventBridge patterns that AWS may reject; TAF performs
  deterministic framework-side envelope/schema validation for the supported contract.

## `1.0.0`

### Added

- Java 25 multi-module Maven build with a dependency-management BOM and Maven Wrapper.
- Runtime core contracts for typed, named, lazy controllers, test-session lifecycle, scoped cleanup, artifacts, diagnostics, and structured failures.
- Optional Runtime modules for local secret resolution, file validation, versioned test definitions (including MongoDB), Playwright browser automation, Appium Android automation, REST and SOAP clients, OpenAPI/AsyncAPI contracts, JDBC, observability, data migration, WireMock virtualization, environment provisioning, Kafka, RabbitMQ, JMS, Allure reporting, TestNG, and Cucumber.
- MCP server modules for contracts, persistent jobs, authorization and audit policy, bounded execution workers, curated tools/resources/prompts, and STDIO and Streamable HTTP transports.
- Consumer conformance checks, a Playwright Sauce Demo example, documentation verification, compatibility and architecture profiles, staged-release consumer smoke tests, and opt-in environment-dependent verification workflows.
- Apache License 2.0 licensing metadata and release-staging support for source, Javadoc, and CycloneDX SBOM generation.

### Changed

- The controller lifecycle is a deliberate redesign: controllers are resolved by type and name, initialized lazily, and cleaned up with their owning test session. No legacy `TestController` adapter is provided.
- Reporting contracts remain vendor-neutral; Allure integration is isolated in its adapter module.
- Infrastructure provisioning is separated from controller implementations.

### Security

- Secret values are kept behind reference/resolver contracts and redacted at framework output boundaries covered by the implementation.
- MCP inputs, workspace operations, authorization decisions, approvals, audit records, and transport behavior have dedicated validation and test infrastructure. These checks do not by themselves constitute a production security certification.

### Known limitations

- Browser, mobile, container, messaging, database, Testcontainers, and Kind verification depend on opt-in tools or environments and are not exercised by every local build.
- The MCP server exposes governed workflow-level operations; it is not a general-purpose raw shell, browser, SQL, or filesystem interface.
- Publishing to a public artifact repository is separately authorized and is not performed by the ordinary build or pull-request workflows.
- Vulnerabilities must be submitted through the repository's private vulnerability-reporting interface. See [SECURITY.md](SECURITY.md).

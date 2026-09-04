# Changelog

All notable user-visible changes to Codinglair TAF will be documented in this file. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project intends to use [Semantic Versioning](https://semver.org/spec/v2.0.0.html) for published releases.

## Unreleased

## <!-- taf-version -->`1.0.0`

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

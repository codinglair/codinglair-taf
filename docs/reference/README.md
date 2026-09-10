# Codinglair TAF <!-- taf-version -->`1.0.0` reference

This is the versioned reference-documentation entry point shipped with the <!-- taf-version -->`1.0.0`
artifacts. Start with the [Quick Start](../quick-start.md), then choose the audience that matches
your work.

| Audience | Reference |
| --- | --- |
| TAF user | [Public API, configuration, controllers, environments, and reporting](runtime-and-configuration.md) |
| New contributor | [Installation and prerequisites](installation-and-prerequisites.md) |
| Test author | [TestNG and Cucumber separation](testng-cucumber-separation.md) |
| Mobile engineer | [Android and Appium setup](android-appium-setup.md) |
| MCP client or operator | [MCP contracts, security, approvals, and audit](mcp-and-security.md) |
| Platform operator | [CI/CD, Kind, release, and troubleshooting](operations-and-troubleshooting.md) |
| Extension author | [Controller and provider extension workflow](extension-spi.md) |
| Maintainer | [Documentation, compatibility, and release workflow](maintainer-guide.md) |

## Version and authority

Documentation has the same version as the Maven artifact that contains or accompanies it. Public
Java member signatures are generated from source by the release Javadoc build. Configuration
entries come from `@ConfigurationProperties` types and Spring configuration metadata. MCP wire
contracts come from the versioned JSON schemas under `META-INF/taf/mcp/schema/v1`. If prose and a
published contract differ, the published contract wins and the documentation drift is a defect.

The current compatibility baseline is
[Java/Spring and ecosystem compatibility](../engineering/compatibility-matrix.md).

## Released API artifacts

Every released artifact below has member-level API entries in its generated Javadoc jar. Optional
capabilities depend on Runtime contracts; Runtime never depends on MCP.

| Artifact | Primary API/reference area |
| --- | --- |
| `codinglair-taf-runtime-core` | TestSession, registry, failures, reporting, definitions, migration |
| `codinglair-taf-common` | shared dependency-minimal value contracts |
| `codinglair-taf-runner-testng` | TestNG invocation lifecycle |
| `codinglair-taf-runner-cucumber` | Cucumber scenario lifecycle |
| `codinglair-taf-reporting-allure` | Allure adapter and single-file publishing |
| `taf-web-playwright` | Browser controller and named settings |
| `taf-api-rest` | REST controller |
| `taf-api-soap` | SOAP controller |
| `taf-database` | JDBC controller |
| `taf-file` | Structured-file validation |
| `taf-observability` | Log, metric, and trace assertions |
| `taf-messaging-core` | Messaging SPI and test kit |
| `taf-messaging-kafka` | Kafka adapter |
| `taf-messaging-rabbitmq` | RabbitMQ adapter |
| `taf-messaging-jms` | JMS/EMS-compatible adapter |
| `taf-messaging-aws` | Optional typed SQS and EventBridge controllers |
| `taf-mobile-core` | Mobile provider contracts |
| `taf-mobile-appium` | Android Appium controller |
| `taf-environments` | Environment providers, preflight, Testcontainers |
| `taf-secrets-api` | Secret-reference contracts |
| `taf-secrets-local` | Authorized local resolution boundary |
| `taf-test-definitions` | Versioned repository SPI and file providers |
| `taf-test-definitions-mongodb` | MongoDB repository provider |
| `taf-data-migration` | JDBC/MongoDB migration managers |
| `taf-virtualization-wiremock` | WireMock service virtualization |
| `taf-contracts` | OpenAPI/AsyncAPI contract validation |
| `taf-consumer-conformance` | Consumer blueprint validator |
| `taf-mcp-contracts` | Versioned MCP wire contracts |
| `codinglair-taf-mcp` | MCP dependency-management parent |
| `taf-mcp-jobs` | Persistent job state machine |
| `taf-mcp-security` | authorization, approval, audit, redaction |
| `taf-execution-worker` | isolated execution worker |
| `taf-mcp-tools` | coarse-grained MCP tools |
| `taf-mcp-resources` | bounded MCP resources |
| `taf-mcp-prompts` | curated MCP prompts |
| `taf-mcp-transport-stdio` | STDIO transport |
| `taf-mcp-transport-http` | Streamable HTTP/OIDC transport |

Consumer projects are governed by the versioned
[consumer-project-blueprint-v1 schema](../architecture/schemas/consumer-project-blueprint-v1.schema.json);
the packaged copy in `taf-consumer-conformance` must remain identical.

## Executable demonstrations and training

The sole released executable consumer demonstration is the
[Sauce Demo Playwright golden project](../../demos/playwright-sauce-demo/README.md). It shows
Spring Boot composition, `TestSession`, TestNG and Cucumber as independent runners, Playwright page
objects, test definitions, preflight, reporting, and secret references. Its default tests are
deterministic; live browser/container profiles require the prerequisites stated in its README.

The [Quick Start](../quick-start.md) supplies compiled REST, database, front-to-back, and
presentation-only capability examples. The [introductory training session](training-session-guide.md)
turns those assets into a repeatable instructor-led or self-guided walkthrough. No other directory
is represented as an executable demonstration project.

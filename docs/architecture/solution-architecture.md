# Codinglair TAF <!-- taf-version -->`1.3.0` public solution architecture

## Scope

This document describes the current public architecture for TAF Runtime and the TAF MCP Server.
It intentionally excludes internal design rationale, commercial plans, proprietary strategy, and
unreleased roadmap commitments. Source, public schemas, Maven metadata, configuration metadata,
tests, and accepted ADRs remain authoritative.

## Product boundaries

TAF Runtime is deterministic and usable without MCP or AI. It supplies invocation-scoped test
lifecycle, optional controllers, environments, test definitions, secrets, reporting, and TestNG
and Cucumber integration. The MCP Server depends on Runtime and exposes governed framework
interaction; Runtime never depends on MCP. Community artifacts never depend on proprietary code.

```mermaid
flowchart LR
  Tests[Consumer tests] --> Runners[TestNG or Cucumber]
  Runners --> Session[TestSession and ControllerRegistry]
  Session --> Caps[Capability modules]
  Session --> Env[Environment providers]
  Session --> Evidence[Reporting and evidence]
  Client[MCP client] --> Transport[STDIO or Streamable HTTP]
  Transport --> MCP[MCP tools, resources, prompts, security and jobs]
  MCP --> Worker[Bounded execution worker]
  Worker --> Tests
```

## Starter and module architecture

Normal consumers select capability-oriented POM starters:

- `codinglair-taf-starter-web`, `-api`, `-database`, and `-mobile`;
- provider-neutral `codinglair-taf-starter-messaging`; and
- provider starters `-messaging-kafka`, `-rabbitmq`, `-jms`, and `-aws`.

Each top-level starter composes its capability with the shared Runtime/TestSession, environment,
secrets, test-definition, reporting, and TestNG foundation. Multiple starters share this foundation
through Maven mediation. The BOM aligns a compatible version set but installs no capability.
Starters contain no implementation classes and classpath presence does not activate a configured
controller or infrastructure resource.

Messaging provider starters transitively compose the generic messaging starter and exactly one
provider. The generic starter is useful to extension authors implementing the provider SPI but is
not operational by itself. Advanced consumers may continue to select supported direct modules for
fine-grained dependency control. Internal support, MCP server, worker, build, demo, and conformance
artifacts are not consumer capabilities. See the [dependency guide](../reference/consumer-dependencies.md)
and [machine-readable starter manifest](../reference/starter-capability-manifest-v1.json).

## Runtime lifecycle and capabilities

`TestSession` owns one invocation's named, typed `ControllerRegistry`, correlation context,
reporting, evidence, and scoped cleanup. Controllers initialize lazily, several instances of one
type may coexist, and cleanup is reverse ordered and idempotent. Controllers consume provisioned
resources; `EnvironmentProvider` implementations own provisioning and cleanup.

Optional public capabilities include Playwright web, REST and SOAP, JDBC, structured files,
Appium/Android and Apple/XCUITest, Kafka, RabbitMQ, JMS, EventBridge, SQS, contracts, WireMock,
observability, data
migration, test-definition providers, and environment providers. Conditional auto-configuration
keeps unrelated technologies inactive and absent.

## Apple mobile architecture for <!-- taf-version -->`1.3.0`

The <!-- taf-version -->`1.3.0` Apple increment extends the existing `taf-mobile-appium` module and
`codinglair-taf-starter-mobile`; it does not introduce an Apple-only dependency graph or change
the Android default. A shared immutable manifest describes iPhone/iPad, native/hybrid/Safari,
simulator/physical and local/remote/provider-compatible selections. Platform strategies preserve
XCUITest semantics instead of manufacturing Android parity. Named controllers remain lazy and
session-scoped, and multiple Apple controllers may coexist in one `TestSession`.

Controllers consume prepared Appium/XCUITest endpoints and authorized targets. Customer or
provider infrastructure owns macOS/Xcode, devices, simulators, signing and WDA. Exact endpoint
paths and server-visible application references are preserved. Remote JVM and MCP clients need no
local Apple toolchain. Passive readiness reports observable configuration and prerequisite state;
target/WDA/signing facts that cannot be observed remotely remain unknown until initialization.

Each session owns its driver, contexts, resource reservations and evidence namespace. Observable
target/WDA/MJPEG/derived-data collisions are rejected, while providers/operators coordinate
resources across processes. Screenshots, page source, genuine XCUITest logs and optional video
flow through `ArtifactCollector`, redaction and the reporter-neutral contract before idempotent
owned cleanup. Artifact availability is explicit and does not silently redefine readiness.

MCP advertises `mobile.apple` through the same versioned catalog and governed discovery,
validation, scaffolding, execution and cancellation workflows over STDIO and Streamable HTTP.
Discovery allocates no device. The MCP image is a remote client and contains no Xcode, WDA,
simulator or device. Generic Appium-compatible endpoint support is distinct from named-provider
certification.

This Apple scope partially supersedes ADR-013's Apple/hybrid/Safari deferral through
[ADR-031](adrs/ADR-031_extend-the-existing-mobile-module-and-starter-with-xcuitest-strategies.md),
[ADR-032](adrs/ADR-032_consume-external-apple-infrastructure-with-topology-aware-preflight.md),
[ADR-033](adrs/ADR-033_preserve-platform-semantics-and-isolate-apple-sessions.md),
[ADR-034](adrs/ADR-034_integrate-apple-evidence-and-consumer-surfaces-through-existing-contracts.md)
and [ADR-035](adrs/ADR-035_qualify-apple-compatibility-with-the-existing-android-verification-approach.md).
ADR-013's Android baseline, provisioning separation and tiered verification remain applicable.
The [Apple consumer guide](../reference/apple-appium-consumer-guide.md) and
[compatibility record](../reference/apple-appium-compatibility-1.3.0.md) define the public
configuration and evidence limits.

The AWS messaging module uses named profiles and independent EventBridge and SQS controller
instances. LocalStack provides deterministic local AWS integration and Testcontainers-based
qualification without changing the public controller contracts. This provider boundary permits
additional AWS integrations in later compatible versions; it makes no commitment to a specific
future capability.

## Configuration, secrets, reporting, and data

Spring Boot 4 is the composition baseline while Runtime Core remains Spring-light. Public settings
use typed configuration properties. Secrets cross consumer, test-definition, and MCP boundaries as
opaque references and resolve only inside an authorized execution boundary. Resolved values must
not enter logs, reports, screenshots, artifacts, MCP payloads, exceptions, or audit records.

TAF-owned reporting contracts remain vendor neutral. Allure is isolated in its adapter, and
controller evidence passes through `ArtifactCollector`. Test definitions are separate from
transient session state, results, large artifacts, audit records, and SUT data. MongoDB is the
recommended optional repository behind the test-definition SPI.

## MCP server architecture

The MCP Server provides coarse-grained discovery, validation, scaffolding/configuration services,
controlled build and execution, inspection, cancellation, resources, and reporting over versioned
contracts. A deployed transport exposes only the operations in its advertised tool catalog;
server-side scaffolding classes are not by themselves a callable MCP operation.
Tools do not expose unrestricted shell, SQL, browser, broker, or filesystem access. Security
centralizes OIDC/OAuth identity, authorization, approval, audit, size/time limits, workspace
confinement, and redaction. Long work uses bounded jobs and controlled artifact references.

STDIO and Streamable HTTP adapt the same governed services. STDIO is a client-launched local
subprocess: protocol frames use stdout and logs use stderr. Streamable HTTP uses `/mcp` behind the
configured resource-server boundary and is the supported transport for detached containers, CI,
and Kubernetes.

## Official container and deployment topology

The official image is `codinglair/codinglair-taf-mcp`. Immutable semantic tags such as `1.2.0` are
authoritative; `latest` is movable and prohibited for reproducible CI and supported Kubernetes.
One image provides mutually exclusive `stdio` and `streamable-http` profiles.

Local STDIO uses `docker run --rm -i` without a pseudo-TTY or published port. Streamable HTTP uses
port 8080 by default and publishes liveness/readiness groups. Docker and CI provide external
configuration, secret references, bounded writable workspace/state, non-root execution, and
graceful termination.

The supported Kubernetes reference uses Streamable HTTP, an immutable image digest, a ClusterIP
Service, probes, ConfigMap/Secret references, non-root/read-only security, resource bounds,
NetworkPolicy intent, and explicit writable volumes. The current single-replica profile has
process-local jobs, approvals, audit, and retained-result state; its `emptyDir` storage is not
durable. Multi-replica or HA operation requires those authorities to be externalized and qualified.
See the [container guide](../operations/mcp-container-deployment.md) and
[Kubernetes reference](../operations/mcp-kubernetes-reference.md).

## Compatibility and authoritative references

Java 25 and Spring Boot 4 are the public platform baseline. TestNG and Cucumber remain independent;
the new typed/named controller lifecycle is the approved lifecycle. Public compatibility is
governed by published contracts and the accepted ADRs, especially
[ADR-026](adrs/ADR-026_publish-capability-oriented-starters-with-provider-specific-messaging-starters.md),
[ADR-029](adrs/ADR-029_distribute-one-mcp-image-with-stdio-and-streamable-http-profiles.md), and
[ADR-030](adrs/ADR-030_gate-releases-with-external-starter-and-blueprint-conformance.md).

Consumer entry points are the [Quick Start](../quick-start.md),
[dependency guide](../reference/consumer-dependencies.md),
[blueprint architecture](consumer-project-blueprint.md), and MCP operational guides. The general
documents describe supported 1.2.0 behavior. The explicitly versioned Apple sections describe the
1.3.0 candidate scope and do not imply publication or completed live qualification.

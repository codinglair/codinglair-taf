# Runtime, capability, and configuration reference

## Lifecycle and controller model

`TafBaseTest` creates one `TestSession` per TestNG invocation and `TafCucumberHooks` creates one per
Cucumber scenario. Consumers must not create sessions themselves. Register controllers by type and
logical name and acquire them lazily from the active session. A session closes initialized
controllers in reverse order; cleanup is idempotent. Do not cache session/controller state in
singletons or unmanaged thread locals. Controllers consume `EnvironmentResource` descriptions;
an `EnvironmentProvider` owns provisioning and cleanup.

Every controller operation, including a call through a controller reference retained from an
earlier invocation, verifies the active invocation before reporting or technology side effects.
Missing, closed, stale, or wrong-invocation ownership fails with `TAF-LIFECYCLE-001`. The guard does
not create an implicit session. A vendor-native object returned by a guarded accessor is outside
TAF interception once returned; do not retain that raw handle or use it after the owning invocation.

### Capability guard coverage

| Capability and supported public entry point | Lifecycle boundary | Before side effects | Positive / negative evidence | Limitation |
| --- | --- | --- | --- | --- |
| Playwright `PlaywrightController` and `PlaywrightObjectFactory` | active-session accessor and guarded controller proxy | yes, before page/browser access | Playwright component tests; `CapabilityLifecycleCoverageTest` and `TestSessionLifecycleTest` | a returned Playwright `Page`/`Locator` is a raw vendor handle |
| REST `RestController.execute`, `await`, `nativeSpecification` | guarded controller proxy | yes, before request construction, polling, or native accessor dispatch | REST component tests; shared lifecycle coverage tests | a returned REST Assured specification is a raw vendor handle |
| SOAP `SoapController.exchange` | guarded controller proxy | yes, before transport dispatch | SOAP component tests; shared lifecycle coverage tests | none |
| JDBC `DatabaseController` query/update/transaction/native connection operations | guarded controller proxy | yes, before connection acquisition or SQL | database component tests; shared lifecycle coverage tests | a returned JDBC object is a raw vendor handle |
| Files `FileController` compare/validate operations | guarded controller proxy | yes, before file access | file component tests; shared lifecycle coverage tests | none |
| Contracts used during REST exchange (`RestContractValidator`) | guarded REST controller proxy | yes, before validator dispatch | REST contract tests; shared lifecycle coverage tests | `ContractService` is offline document transformation, not an execution capability |
| Observability `ObservabilityController` logs/metrics/traces | guarded controller proxy | yes, before provider queries | observability component tests; shared lifecycle coverage tests | provider SPIs are controller implementation boundaries |
| Kafka, RabbitMQ, and JMS/EMS controller operations | guarded controller proxy | yes, before publish, consume, polling, or native access | provider contract tests; shared lifecycle coverage tests | returned native clients are raw vendor handles |
| SQS and EventBridge controller operations | guarded controller proxy | yes, before AWS SDK calls or polling | AWS component tests; `CapabilityLifecycleCoverageTest` | returned AWS SDK clients are raw vendor handles |
| Android `AndroidController` and Apple `AppleController` operations | guarded controller proxy | yes, before Appium/device/WDA calls or native access | mobile protocol tests; `CapabilityLifecycleCoverageTest` | returned Appium/Selenium objects are raw vendor handles |
| Capability polling | the owning controller proxy | yes, before entering the controller method that polls | controller component tests; shared lifecycle coverage tests | `AwaitableAssertion` alone is a generic in-memory assertion helper |

Controller factories are construction SPIs used by `TestSessionConfigurer`; supported capability
execution begins only after the result is registered and resolved from the owning session. Runtime
implementation tests that exercise a controller implementation directly may use an explicitly
created JUnit fixture because they test implementation logic, not the supported consumer lifecycle.
The exported Appium platform strategies, JDBC connection factory, observability providers, contract
adapters, and structured file comparator are implementation/provider seams behind those entry
points, not parallel consumer facades. Environment and virtualization factories provision owned
test infrastructure and remain governed by the separate `EnvironmentProvider` lifecycle.

Controller guides and compiled examples are in the [Quick Start](../quick-start.md). The
[capability matrix](../quick-start-capability-matrix.md) maps each released module to its evidence
level and prerequisites.

## Configuration prefixes

The generated Spring metadata in each artifact is authoritative for property type, default, and
description. Named controller maps use `taf.<capability>.controllers.<logical-name>`. Unknown,
missing, or inconsistent required values fail validation with the property path; unresolved secret
references remain opaque until an authorized execution boundary.

| Prefix | Purpose |
| --- | --- |
| `taf` | Runtime identity, timeouts, evidence, and lifecycle defaults |
| `taf.history` | Cross-run history |
| `taf.web.playwright` | Playwright defaults and named controllers |
| `taf.api.rest` | REST controllers |
| `taf.api.soap` | SOAP controllers |
| `taf.database` | JDBC controllers |
| `taf.file` | file bounds and validation |
| `taf.observability` | logs, metrics, and traces |
| `taf.messaging.kafka` | Kafka controllers |
| `taf.messaging.rabbitmq` | RabbitMQ controllers |
| `taf.messaging.jms` | JMS controllers |
| `taf.aws` | named AWS profiles with SQS and EventBridge controller instances |
| `taf.mobile.android` | Android/Appium controllers |
| `taf.mobile.apple` | Disabled by default; typed Apple/XCUITest configuration in the mobile starter. See [module configuration](../../codinglair-taf-runtime/taf-mobile-appium/README.md) and [transport/resource security](apple-transport-security.md). |
| `taf.environment` | provider lifecycle and policy |
| `taf.consumer` | capability declarations and preflight |
| `taf.secrets` | local resolver policy |
| `taf.context.mongodb` | MongoDB test-definition provider |
| `taf.migration` | migration selection and policy |
| `taf.migration.mongodb` | isolated MongoDB migration jobs |
| `taf.virtualization.wiremock` | WireMock provider |
| `taf.reporting.allure.single-file` | single-file report publisher |
| `taf.mcp.application` | shared production service composition, workspace, and persistent state roots |
| `taf.mcp.stdio` | STDIO limits and lifecycle |
| `taf.mcp.http` | HTTP/OIDC limits and lifecycle |
| `consumer.environment` | clean consumer-smoke environment identity |
| `taf.demo` | Sauce Demo reference-consumer settings (not a Runtime contract) |

`taf.mcp.http.workspace-root` confines remote scaffold destinations. Its local-development default
is the server process working directory (`.`); relative values are resolved against that directory.
Container, CI, and Kubernetes deployments should set an explicit absolute path to the approved
writable workspace mount. The server rejects the root itself, traversal outside it, unavailable
parents, and symbolic-link parents before generation.

Secret provider availability is separate from activation. A production-like profile must configure
one `taf.secrets.provider` value, or explicit routes such as
`taf.secrets.routing.vault=vaultSecretProvider`. Route keys are provider IDs and values are Spring
bean names. The explicitly activated `taf-local` profile selects only the environment provider.
Omitting selection, mixing the single-provider and routing forms, or selecting an unavailable or
ambiguous bean fails startup. Configuration and generated projects contain only references such as
`secret://env/ORDERS_API_TOKEN`; resolved values are prohibited from configuration, diagnostics,
logs, reports, artifacts, and MCP responses.

## Environments and Testcontainers

Use external mode when infrastructure is supplied by an operator and container mode when a
provider owns an isolated fixture. Never configure fixed container ports. Readiness is `ready`,
`degraded`, `unavailable`, `misconfigured`, or `unknown`; degraded behavior follows explicit
policy. Shared resources are reference-counted and scoped resources are invocation-owned. Provider
cleanup must tolerate partial initialization, cancellation, and repeated close.

Container mode is explicit and default-safe:

```yaml
taf:
  environment:
    mode: container
    testcontainers-enabled: true
    lifecycle: isolated
    startup: lazy
    readiness-timeout: 60s
    log-capture: true
    cleanup: always
```

Use `lifecycle: shared` only for a provider designed for reference-counted sharing. Tests must not
assume a fixed host port. The provider publishes its dynamic connection description after
readiness; named controllers consume that resource through their session context.

For operator-provisioned infrastructure, keep provisioning outside TAF and select external mode:

```yaml
taf:
  environment:
    mode: external
    testcontainers-enabled: false
  consumer:
    environment: integration
```

Bind endpoint/user details through the capability's typed configuration and bind credentials as
opaque `secret://` references. Run consumer preflight before controller acquisition. An unavailable
external target is an environment failure, not a product failure, and TAF cleanup must not destroy
operator-owned infrastructure.

## Consolidated configuration example

This example composes two named capabilities without resolved secrets. Adapt endpoints and names to
the SUT contract; the generated Spring metadata remains authoritative for every field.

```yaml
taf:
  consumer:
    environment: local
  web:
    playwright:
      enabled: true
      controllers:
        storefront:
          base-url: https://example.invalid
  api:
    rest:
      enabled: true
      controllers:
        orders:
          base-uri: https://api.example.invalid
```

See the compiled [Quick Start configuration](../quick-start.md) before adding database, messaging,
or mobile settings. Unknown fields should not be used to infer a capability.

AWS messaging is optional. It uses separate typed controllers while sharing a named connection
profile. Credential configuration accepts only an opaque `credential://` profile reference; access
keys and session tokens are not configuration properties.

```yaml
taf:
  aws:
    enabled: true
    profiles:
      local:
        endpoint-mode: localstack
        endpoint-override: http://localhost:4566
        region: us-east-1
        ownership-mode: test-owned
        policy:
          operation-timeout: 30s
          poll-interval: 250ms
          maximum-receive-messages: 10
          maximum-visibility: 15m
          maximum-evidence-bytes: 16384
        sqs:
          orders:
            queue: http://localhost:4566/000000000000/orders
            dead-letter-queue: http://localhost:4566/000000000000/orders-dlq
            isolation-mode: dedicated-resource
            unmatched-message-policy: restore-immediately
        eventbridge:
          orders:
            event-bus: orders
            target-sqs-controller: orders
            target-identity: orders-queue
            envelope-schema: >-
              {"type":"object","required":["source","detail-type","detail"]}
```

Acquire instances with
`session.getControllerRegistry().get(SqsController.class, "orders")` and
`session.getControllerRegistry().get(EventBridgeController.class, "orders")`. Receipt handles are
available only on session-scoped `ReceivedSqsMessage` values and must not be logged or persisted.
SQS receive, wait, and negative-wait operations are bounded by both the request and profile policy.
`EXTERNAL_SHARED` is intentionally rejected: operator-owned shared queues must declare
`CONTROLLED_CONSUMER` or `MIRROR_QUEUE`. Non-matching messages are released immediately and are
never implicitly deleted; unacknowledged matching messages are released when the session closes.
Queue and DLQ counts are approximate diagnostics. `SqsMessageEvidence` contains sanitized,
size-bounded payload and attribute evidence but never a receipt handle.

`EventBridgeController.publish(...)` accepts one to ten structured entries with source, detail
type, JSON-object detail, resources, metadata, correlation identity, and optional AWS trace header.
TAF places correlation and metadata under `detail._taf`, returns index-stable per-entry results,
and retains bounded sanitized request evidence. The original five-argument request constructor
remains supported; resources and trace header default to absent.

Route verification is explicit composition rather than EventBridge browsing. Configure
`target-sqs-controller`, `target-identity`, and an optional JSON Schema 2020-12 envelope schema,
then call `verifyRoute(EventRouteRequest, SqsController)`. The operation publishes through the
EventBridge controller, waits through the separately acquired SQS controller, validates the actual
target envelope and correlation identity, and returns sanitized SQS evidence. Use
`assertNotRouted(...)` for a full bounded non-matching observation. Both controllers remain usable
independently and neither operation provisions or destroys infrastructure.

AWS stable evidence is published as `AWS_EVIDENCE` artifacts through the owning `TestSession`'s
`ArtifactCollector`. Payloads and metadata are sanitized independently, bounded by
`maximum-evidence-bytes`, and accompanied by the SHA-256 digest and original UTF-8 byte count.
Receipt handles, trace headers, raw SDK request/result objects, authorization material, resolved
credentials, and raw exception causes are omitted. Failure, timeout, assertion, diagnostics, and
cleanup evidence uses the same boundary. Safe message IDs, correlation IDs, receive counts,
timestamps, target identity, and ownership-safe queue diagnostics remain available for diagnosis.

For mode-by-mode configuration, the isolation decision, standalone walkthrough, compatibility
profile, MCP examples, and contributor extension contract, see the
[AWS messaging guide](aws-messaging.md).

## Reporting, evidence, and redaction

TAF reporting annotations describe meaningful public actions. Nested helper calls must not create
duplicate steps. Controllers publish evidence through `ArtifactCollector`, never directly to
Allure. Evidence names, text, DOM, HTTP bodies, broker records, SQL diagnostics, and MCP output are
bounded and sanitized before persistence. Store secret references—not values—and verify redaction
with a canary when adding an evidence path. Allure-specific annotations and lifecycle calls stay in
`codinglair-taf-reporting-allure`; see [single-file publishing](../reporting/single-file-allure.md).

## Deep dives

- Failure classification and history: [Runtime failure reference](runtime-failure-classification-and-history.md)
- JMS/EMS behavior: [messaging compatibility](messaging-jms-ems-compatibility.md)
- Complete compiled workflows: [Quick Start](../quick-start.md)

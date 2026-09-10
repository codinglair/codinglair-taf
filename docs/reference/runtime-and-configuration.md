# Runtime, capability, and configuration reference

## Lifecycle and controller model

Create one `TestSession` per TestNG invocation or Cucumber scenario. Register controllers by type
and logical name, acquire them lazily, and close the session. A session closes initialized
controllers in reverse order; cleanup is idempotent. Do not cache session/controller state in
singletons or unmanaged thread locals. Controllers consume `EnvironmentResource` descriptions;
an `EnvironmentProvider` owns provisioning and cleanup.

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
| `taf.environment` | provider lifecycle and policy |
| `taf.consumer` | capability declarations and preflight |
| `taf.secrets` | local resolver policy |
| `taf.context.mongodb` | MongoDB test-definition provider |
| `taf.migration` | migration selection and policy |
| `taf.migration.mongodb` | isolated MongoDB migration jobs |
| `taf.virtualization.wiremock` | WireMock provider |
| `taf.reporting.allure.single-file` | single-file report publisher |
| `taf.mcp.stdio` | STDIO limits and lifecycle |
| `taf.mcp.http` | HTTP/OIDC limits and lifecycle |
| `consumer.environment` | clean consumer-smoke environment identity |
| `taf.demo` | Sauce Demo reference-consumer settings (not a Runtime contract) |

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
        sqs:
          orders:
            queue: http://localhost:4566/000000000000/orders
            dead-letter-queue: http://localhost:4566/000000000000/orders-dlq
            isolation-mode: dedicated-resource
            unmatched-message-policy: restore-immediately
        eventbridge:
          orders:
            event-bus: orders
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

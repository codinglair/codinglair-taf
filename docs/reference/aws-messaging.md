# AWS messaging guide

The optional `taf-messaging-aws` Runtime module provides separate, independently usable
`SqsController` and `EventBridgeController` contracts. Both are named, lazy controllers owned by a
`TestSession`; neither controller provisions or destroys infrastructure. Use
`LocalStackEnvironmentProvider` for local provisioning and use operator- or IaC-provisioned
resources for external LocalStack and authorized AWS.

## Add the capability

Import the Codinglair TAF BOM and add the module without a version:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.codinglair.taf</groupId>
      <artifactId>codinglair-taf-bom</artifactId>
      <version>1.1.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
<dependencies>
  <dependency>
    <groupId>com.codinglair.taf</groupId>
    <artifactId>taf-messaging-aws</artifactId>
  </dependency>
</dependencies>
```

Java 25 and Docker are required for the managed LocalStack walkthrough. Docker is not required for
an external endpoint.

## Configuration reference

All waits are bounded by the smaller of the request timeout and `operation-timeout`.

| Property | Default and boundary | Purpose |
| --- | --- | --- |
| `taf.aws.enabled` | `false` | Enables AWS session composition. |
| `taf.aws.localstack-image` | release-managed `localstack/localstack:4.14.0` | Managed provider image; CI pins its approved digest. |
| `profiles.<name>.region` | required | AWS signing region. |
| `endpoint-mode` | `AWS` | `LOCALSTACK` or `AWS`. |
| `endpoint-override` | optional HTTP(S) URI | Required for declared external LocalStack; omit for AWS. User-info, query, and fragment are rejected. |
| `credential-profile-reference` | optional `credential://...` reference | Opaque authorized-AWS credential profile; never a key or token. |
| `ownership-mode` | `EXTERNAL` | `TEST_OWNED` only for managed LocalStack; `EXTERNAL` resources are preserved. |
| `policy.operation-timeout` | `30s`; 100 ms–5 min | Per-operation ceiling. |
| `policy.poll-interval` | `250ms`; 10 ms–operation timeout | Bounded local polling interval. |
| `policy.retry-attempts` | `3`; 0–10 | Attempts for operations explicitly classified as retry-safe. |
| `policy.maximum-receive-messages` | `10`; 1–10 | Administrative SQS receive batch limit. |
| `policy.maximum-visibility` | `15m`; 0–12 h | Administrative visibility-change limit. |
| `policy.maximum-evidence-bytes` | `16384`; 0–1,048,576 | UTF-8 evidence limit before explicit truncation. |
| `sqs.<instance>.queue` | required | Queue URL or provider-resolved physical identifier. |
| `sqs.<instance>.dead-letter-queue` | optional | DLQ URL used only for approximate diagnostics. |
| `sqs.<instance>.isolation-mode` | `DEDICATED_RESOURCE` | See the isolation decision below. |
| `sqs.<instance>.unmatched-message-policy` | `RESTORE_IMMEDIATELY` | The only supported policy; unmatched messages are not deleted. |
| `eventbridge.<instance>.event-bus` | required | Event bus name or ARN. |
| `target-sqs-controller`, `target-identity` | optional, both or neither | Named SQS target used for route verification. |
| `event-pattern` | match any source | Provisioned rule pattern in managed mode. |
| `envelope-schema` | optional | JSON Schema 2020-12 validation for the observed SQS envelope. |

Spring Boot relaxed binding accepts the lowercase kebab-case values shown below.

### Managed local mode

Use the environment provider to start LocalStack on a dynamic port and create isolated resources.
Do not set an endpoint override or fixed host port in provisioning configuration.

```yaml
taf:
  aws:
    enabled: true
    profiles:
      local:
        endpoint-mode: localstack
        region: us-east-1
        ownership-mode: test-owned
        policy:
          operation-timeout: 30s
          poll-interval: 100ms
        sqs:
          orders-target:
            queue: orders
            dead-letter-queue: orders-dlq
            isolation-mode: dedicated-resource
        eventbridge:
          orders-events:
            event-bus: orders
            target-sqs-controller: orders-target
            target-identity: orders-target
            event-pattern: '{"source":["orders.created"]}'
```

The provider returns an `AwsEnvironmentConfiguration` containing the dynamic endpoint and physical
resource identifiers. Supply those effective values to the application context that creates the
`TestSession`, as demonstrated by the compiled standalone consumer linked below.

### Declared external LocalStack

The operator starts LocalStack and creates the queue, policy, bus, rule, and target. TAF performs
read-only preflight and preserves every resource.

```yaml
taf:
  aws:
    enabled: true
    profiles:
      integration:
        endpoint-mode: localstack
        endpoint-override: http://localstack.test.invalid:4566
        region: us-east-1
        ownership-mode: external
        sqs:
          orders-tap:
            queue: http://localstack.test.invalid:4566/000000000000/orders-tap
            isolation-mode: mirror-queue
        eventbridge:
          orders-events:
            event-bus: orders
            target-sqs-controller: orders-tap
            target-identity: orders-tap
```

### Authorized AWS

Use dedicated non-production resources, least privilege, an approved region/account boundary, and
an opaque credential reference. TAF does not create or delete these resources.

```yaml
taf:
  aws:
    enabled: true
    profiles:
      qualification:
        endpoint-mode: aws
        region: us-east-1
        ownership-mode: external
        credential-profile-reference: credential://authorized-aws-qualification
        sqs:
          orders-tap:
            queue: https://sqs.us-east-1.amazonaws.com/000000000000/taf-orders-tap
            isolation-mode: mirror-queue
        eventbridge:
          orders-events:
            event-bus: arn:aws:events:us-east-1:000000000000:event-bus/taf-orders
            target-sqs-controller: orders-tap
            target-identity: orders-tap
```

The identifiers are non-secret examples. Never place access keys, session tokens, resolved
credentials, authorization headers, credential values, or receipt handles in configuration,
command lines, test data, logs, MCP requests, or evidence.

## Choose safe SQS isolation

| Situation | Select | Rule |
| --- | --- | --- |
| Provider creates a queue for one run | `DEDICATED_RESOURCE` | Recommended default; managed LocalStack only. |
| Provider creates a run-specific namespace | `DEDICATED_NAMESPACE` | Managed LocalStack only. |
| Operator grants this test exclusive consumer ownership | `CONTROLLED_CONSUMER` | External queue is preserved; coordinate all consumers. |
| Existing traffic is copied to a test-only tap | `MIRROR_QUEUE` | Preferred external/shared-infrastructure pattern. |
| Multiple uncontrolled consumers share one queue | none | Unsafe. `EXTERNAL_SHARED` is rejected; correlation filtering does not make destructive receive safe. |

SQS has no browse operation: receive changes visibility. TAF immediately restores unmatched
messages, retains matching messages only in the owning controller, and requires explicit
acknowledgment. Closing the session releases session-owned in-flight visibility and is idempotent;
it does not delete unacknowledged messages. Receipt handles exist only inside
`ReceivedSqsMessage`; never log, persist, return, or copy them to evidence.

Use a positive, finite timeout in every `SqsReceiveRequest`. `awaitMessage` fails if no match is
found. `assertNoMatchingMessage` succeeds only after observing the complete bounded interval.
Approximate queue/DLQ counts from `diagnostics()` are troubleshooting signals, not exact assertion
values. A DLQ/redrive policy is provisioned or managed outside the controller; validate redrive by
bounded repeated receives and the configured DLQ, not by purging a queue.

## Controller walkthrough

Acquire both named controllers from the same session. The logical names must match configuration.

```java
try (TestSession session = sessionFactory.create()) {
  SqsController sqs = session.getController(SqsController.class, "orders-target");
  EventBridgeController events =
      session.getController(EventBridgeController.class, "orders-events");

  var event = new EventPublishRequest(
      "orders.created",
      "order-created",
      "{\"orderId\":\"42\"}",
      List.of("arn:example:order:42"),
      Map.of("scenario", "documentation"),
      "doc-order-42",
      null);

  EventRouteResult routed = events.verifyRoute(
      new EventRouteRequest(event, Duration.ofSeconds(10)), sqs);
  sqs.acknowledgeByMessageId(routed.targetEvidence().messageId());

  events.assertNotRouted(
      new EventRouteRequest(
          new EventPublishRequest(
              "orders.ignored", "order-created", "{\"orderId\":\"43\"}",
              List.of(), Map.of(), "doc-order-43", null),
          Duration.ofSeconds(2)),
      sqs);
}
```

`verifyRoute` publishes to EventBridge, then observes the configured SQS target and validates its
envelope, correlation identity, target identity, and optional schema. EventBridge is not treated as
a browsable store. `publish` and all SQS operations can also be used independently. Send with
`SqsSendRequest`; match by correlation plus selected attributes with `SqsReceiveRequest`; call
`assertBody`/`assertAttributes`, collect sanitized `evidence`, then `acknowledge` explicitly.

Controller and provider ownership are separate. Close `TestSession` before cleaning the
`LocalStackEnvironmentResource`, then call provider cleanup. Provider cleanup deletes only manifest
entries whose creation source is TAF and cleanup policy is delete, in reverse dependency order.
External entries are always preserved. Cleanup is safe to repeat and must run in `finally` or an
equivalent lifecycle hook after success, failure, timeout, interruption, or cancellation.

## LocalStack quick start

The clean-room consumer is outside the Maven reactor and uses only staged public artifacts. From a
clean checkout with Java 25 and Docker running:

```powershell
.\mvnw.cmd clean deploy -Drevision=1.1.0 -Prelease-staging
.\mvnw.cmd -N -Drevision=1.1.0 -Pconsumer-smoke verify
```

```sh
./mvnw clean deploy -Drevision=1.1.0 -Prelease-staging
./mvnw -N -Drevision=1.1.0 -Pconsumer-smoke verify
```

The second command uses Maven Invoker, an isolated repository, dynamically mapped LocalStack, and
the complete public-API scenario in
[`AwsMessagingConsumerSmokeTest`](../../release/consumer-smoke/aws-messaging/src/test/java/com/codinglair/taf/smoke/AwsMessagingConsumerSmokeTest.java).
It verifies route delivery, bounded negative observation, evidence redaction, acknowledgment, and
owned-resource cleanup. See the [standalone consumer README](../../release/consumer-smoke/aws-messaging/README.md)
for staging details.

### Troubleshooting

- Docker unavailable: run `unit-contract` and `mcp-contract-leak`; container and consumer gates
  require Docker. No manual port forwarding is needed.
- Provisioning is unavailable or misconfigured: inspect the sanitized environment diagnostic and
  check the requested provider mode, image availability, region, and required resource names.
- External preflight fails: verify the endpoint is reachable and the operator-provisioned queue,
  bus, rule, target, and policies exist. TAF will not repair external infrastructure.
- Unsafe queue error: replace shared correlation scanning with an exclusive controlled consumer or
  a mirror/tap queue.
- Wait timeout: check the rule pattern, queue policy, target identity, correlation value, and the
  request/profile timeout. Do not add an unbounded loop or arbitrary sleep.
- Repeated delivery: acknowledge a matched message explicitly; otherwise visibility expiry and
  redrive are expected SQS behavior. Use the evidence receive count and approximate diagnostics.
- Evidence is truncated: raise `maximum-evidence-bytes` only within policy; use its digest and
  original byte count to identify the complete sanitized payload.

## Compatibility profile

Release 1.1.0 targets Java 25, Spring Boot 4.x, AWS SDK for Java 2.42.15, Testcontainers 2.0.5,
and LocalStack 4.14.0. CI uses the digest recorded in the
[dependency compatibility decision](../architecture/decisions/AWS-110-001-dependency-compatibility.md).

Qualified on LocalStack: EventBridge publish and rule-to-SQS routing; SQS send, bounded receive,
correlation/attribute matching, acknowledgment, visibility recovery, approximate diagnostics,
redrive/DLQ behavior; managed/external readiness, parallel namespace isolation, preservation, and
idempotent cleanup. Known portability limits:

- LocalStack emulates AWS; passing it is not authorized-AWS qualification and does not prove IAM,
  quotas, latency, regional behavior, service limits, or production policy integration.
- LocalStack signing identity is emulator-only and must never be used for AWS.
- SQS counts and delivery timing are approximate in both environments; tests must not assert exact
  timing or counts.
- EventBridge route proof is target observation through SQS, not event-history inspection.
- The release qualifies the documented operations only. Unsupported AWS APIs and LocalStack
  services are not implied by the shared client/environment foundation.

Optional live qualification requires an approved non-production account, region, resource
boundary, identity, cleanup policy, protected-environment approval, and release authorization.
Run the same bounded route/receive/acknowledge and preservation scenarios against pre-provisioned
external resources; record account/region/resource aliases, versions, timestamps, outcomes, and
sanitized evidence—never resolved credentials or receipt handles. If authorization or the suite is
unavailable, record it as not run and obtain the Gate E release disposition. The current
`authorized-aws` CI job checks only the approval boundary and does not authenticate or execute AWS.

## MCP catalog and safe jobs

The v1 catalog advertises `aws.eventbridge` (`publish`, `verify-route`, `assert-not-routed`) and
`aws.sqs` (`send`, `receive`, `await`, `assert-none`, `acknowledge`, `visibility`, `diagnostics`).
Discovery exposes logical instance names, safe resource aliases, ownership/isolation, supported
operations, environment, and readiness only. Select the exact logical instance configured for the
environment; never submit a physical queue URL, ARN, credential reference, or receipt handle as an
instance name.

Before validation or dispatch, preflight fails closed if the capability is not installed, the
instance does not exist, readiness is not acceptable, the environment is unauthorized, the
ownership/isolation combination is unsafe, or an operation is not allowed. A composed job request:

```json
{
  "schemaVersion": "1.0",
  "requestId": "request-aws-doc-1",
  "operation": "execute",
  "projectId": "orders",
  "environment": "local",
  "timeoutSeconds": 120,
  "idempotencyKey": "doc-request-0001",
  "arguments": {
    "selector": "AwsRouteTest",
    "requiredCapabilities": [
      {"capabilityId":"aws.eventbridge","instance":"orders-events","operations":["publish","verify-route"]},
      {"capabilityId":"aws.sqs","instance":"orders-target","operations":["await","acknowledge"]}
    ]
  }
}
```

Existing v1 jobs may omit `requiredCapabilities`; adding it is backward compatible. Asynchronous
`compile`, `build`, and `execute` responses use status `accepted` plus a bounded `taf://job/...`
reference. Use `inspect` for state/progress, `cancel` for idempotent cancellation, and `retrieve` or
`report` for authorized sanitized results. Responses contain bounded summaries and `taf://`
references; they never contain credentials, raw provider diagnostics, receipt handles, raw SDK
objects, or unrestricted low-level AWS mutation operations.

## Evidence and credential safety

AWS evidence goes through the session `ArtifactCollector`. Payload and metadata are sanitized
independently and bounded; safe evidence includes message/event IDs, correlation IDs, receive
counts, timestamps, target aliases, digests, and ownership-safe diagnostics. It excludes receipt
handles, trace headers, raw SDK requests/responses and causes, authorization material, and resolved
credentials. A `credential://` URI is a reference, not evidence; avoid publishing even references
unless necessary for an approved audit record. Use canary leak tests when extending evidence.

## Extend the environment for another AWS service

Implement `AwsServiceEnvironmentContributor` for service-specific infrastructure; do not add
provisioning to a controller.

1. Return a unique stable service ID from `service()` and describe only the named profile's
   resources from `describeResources(profileName)`.
2. In `provision`, create only request-owned resources. Return immutable `AwsResourceDescriptor`
   values with logical/physical IDs and enough dependency ordering for reverse cleanup.
3. Implement bounded, non-secret `readiness` and `diagnostics`. Use the standard ready, degraded,
   unavailable, misconfigured, or unknown state and actionable sanitized messages.
4. Make `cleanup(profileName, resources)` idempotent, reverse dependency ordered, tolerant of
   already-absent resources, and restricted to TAF-created/delete-authorized descriptors.
5. Register the contributor as a Spring bean. `AwsServiceEnvironmentContributors` discovers an
   immutable service map and rejects duplicate service IDs.
6. Keep its service model and controller contract separate; reuse common endpoint, credential
   reference, budget, retry classification, evidence sanitization, and environment facilities.
7. Add unit, Spring-context, LocalStack integration, cancellation/interruption, concurrent
   isolation, partial-provision rollback, external-preservation, cleanup, and canary leak tests.
8. Update the capability catalog, MCP preflight/serialization contracts, compatibility profile,
   consumer smoke, CI impact paths, and this guide only for operations actually qualified.

Default SPI methods preserve source compatibility, but production contributors should explicitly
implement lifecycle behavior. A future service is not supported merely because LocalStack exposes
an endpoint for it.

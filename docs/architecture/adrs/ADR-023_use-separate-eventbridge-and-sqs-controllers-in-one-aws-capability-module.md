# ADR 023 Use Separate EventBridge and SQS Controllers in One AWS Capability Module

- Status: Accepted
- Date: 2026-09-09
- Target release: codinglair-taf 1.1.0

## Context

EventBridge routes events while SQS stores messages for pull-based consumption. A generic messaging controller would hide rule matching, receipt handles, visibility, redrive, and ownership semantics. Both services share AWS client configuration, endpoint overrides, credential resolution, retries, evidence sanitization, and environment integration.

## Decision

Release 1.1.0 will introduce the optional `taf-messaging-aws` module with separate public `EventBridgeController` and `SqsController` contracts. Internal common services will centralize AWS SDK for Java 2.x client construction, configuration, error classification, bounded polling, ownership enforcement, and evidence sanitization. AWS SDK types will not enter runtime-core contracts and should not become stable framework result types where a framework-owned model is practical.

The module will use `common`, `eventbridge`, `sqs`, and `environment` packages. Future AWS capabilities will add service-specific controllers and environment contributors that reuse the common foundation. The project may split the module later if dependency, lifecycle, or release boundaries justify it, while retaining the public contracts.

## Consequences

- Consumers add one optional module for the initial capability.
- EventBridge and SQS remain independently usable and compose in one `TestSession`.
- Runtime core remains independent of AWS SDK and LocalStack.
- The BOM, compatibility matrix, auto-configuration tests, and consumer smoke must cover the module.

## Alternatives Considered

- One generic AWS or messaging controller was rejected because it erases service-specific behavior.
- Three initial modules were deferred because the first release has one dependency family and shared configuration and lifecycle concerns.

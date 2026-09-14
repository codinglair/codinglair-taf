# ADR 025 Require Non Destructive SQS Isolation and Verify EventBridge Routing Through SQS

- Status: Accepted
- Date: 2026-09-09
- Target release: codinglair-taf 1.1.0

## Context

SQS has no server-side browse operation or arbitrary correlation selector. `ReceiveMessage` changes message visibility, so client-side filtering can interfere with another consumer. EventBridge is not a browsable event store; route verification needs an observable target.

## Decision

Dedicated test-run queues or namespaces are the default. Correlation-based observation is allowed only with exclusive controlled-consumer ownership or a dedicated mirror or tap queue. Preflight rejects correlation-only scanning of a shared queue when another consumer may own unmatched messages.

All receive, wait, and negative assertions are bounded. Receipt handles remain inside session-scoped received-message objects and never enter logs, evidence, durable state, or MCP results. Acknowledgment is explicit. Approximate queue counts are diagnostics rather than exact assertion primitives.

EventBridge route validation composes the controllers: publish a structured event, then await and validate the target SQS envelope and correlation identity. Session cleanup may release visibility for session-owned in-flight messages but will not delete unacknowledged messages unless an approved test-owned cleanup policy requires it.

## Consequences

- Shared queues cannot be declared safe solely because the client filters by correlation ID.
- Consumer guidance and preflight diagnostics must make environment-specific isolation requirements explicit.
- Negative assertions require a complete bounded observation interval.
- EventBridge and SQS remain separate capabilities while supporting end-to-end routing validation.

## Alternatives Considered

- Unrestricted client-side correlation filtering was rejected because it can hide unrelated messages.
- EventBridge storage assertions were rejected because EventBridge does not expose queue-like history for deterministic observation.

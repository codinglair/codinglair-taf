# ADR-025: Require Non-Destructive SQS Isolation and Verify EventBridge Routing Through SQS

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

#### Context

SQS does not provide a server-side browse operation or arbitrary correlation selector. Receiving a message changes its visibility and may affect another consumer even when a client later restores visibility. EventBridge is not a browsable event store, so routing assertions require an observable target such as a test-controlled SQS queue.

#### Decision

• Make dedicated test-run queues or dedicated environment namespaces the default for local, component, integration, and parallel qualification.

• Permit correlation-based observation only when the test owns consumption for the queue or observes a dedicated mirror or tap queue. Preflight shall reject correlation-only observation on a shared queue when another consumer may own unmatched messages.

• Bound every receive, wait, and negative assertion by configured deadlines and polling limits. Never use unbounded polling.

• Keep unmatched messages untouched whenever the selected isolation mode permits it. If a controlled-consumer mode receives an unmatched message, restore its visibility according to policy, record sanitized metadata, and continue within the receive and time budget.

• Expose receipt handles only through session-scoped received-message objects. Do not persist, log, report, or return receipt handles through MCP or stable evidence.

• Implement EventBridge route verification as composition: publish a structured event, then use SqsController to await the expected target envelope and correlation identity.

• Require explicit acknowledgment. Session cleanup may release visibility for session-owned in-flight messages but shall not delete unacknowledged messages unless an approved test-owned cleanup policy says so.

• Treat approximate queue counts as diagnostics, not exact assertion primitives. Use message identity and bounded observation for deterministic assertions.

#### Consequences

• Tests cannot claim safe observation on a shared production-like queue merely because they filter received messages by correlation identifier.

• Consumer examples and preflight errors must explain which isolation modes are safe for each environment.

• Negative assertions require a full bounded observation interval and remain scoped to the configured correlation identity.

• EventBridge and SQS stay independently usable while supporting the principal end-to-end routing scenario.

#### Alternatives Considered

• Client-side filtering on any shared queue was rejected because ReceiveMessage can temporarily hide unrelated messages from their owner.

• Asserting EventBridge storage was rejected because EventBridge does not expose an event history suitable for deterministic queue-style observation.

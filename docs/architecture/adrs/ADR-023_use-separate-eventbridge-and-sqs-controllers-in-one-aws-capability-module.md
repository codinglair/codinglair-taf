# ADR-023: Use Separate EventBridge and SQS Controllers in One AWS Capability Module

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

#### Context

EventBridge routes events while SQS stores messages for pull-based consumption. A lowest-common-denominator messaging controller would hide rule-matching, receipt-handle, visibility, redrive, and queue-ownership semantics. The two capabilities nevertheless share AWS client and operational concerns, and an early three-module split would add release and configuration overhead without an established dependency boundary.

#### Decision

• Introduce the optional taf-messaging-aws Maven module for release 1.1.0.

• Expose distinct SqsController and EventBridgeController public contracts and register named instances through ControllerRegistry.

• Keep shared implementation services internal to the module, including AwsClientFactory, endpoint and credential resolution, retry classification, polling support, ownership checks, and evidence sanitization.

• Use AWS SDK for Java 2.x clients with versions controlled by the project BOM. Keep SDK types out of runtime-core contracts and stable framework result models where practical.

• Organize the module by common, sqs, eventbridge, and environment packages. Split service modules later only when dependency, release, or lifecycle boundaries justify it.

• Add future AWS services through service-specific controllers and auto-configuration that reuse the common AWS foundation; do not widen EventBridge or SQS contracts into a generic AWS controller.

#### Consequences

• Consumers add one optional dependency for the initial AWS messaging capability while retaining typed service behavior.

• The Runtime core remains independent of AWS SDK and LocalStack dependencies.

• A later module split can preserve public controller contracts and configuration namespaces.

• The BOM, compatibility matrix, auto-configuration tests, and standalone consumer smoke must cover the AWS capability.

#### Alternatives Considered

• One generic AWS or messaging controller was rejected because it erases service-specific semantics and weakens compile-time guidance.

• Separate core, SQS, and EventBridge modules were deferred because the initial capability has one dependency family and shared configuration and lifecycle concerns.

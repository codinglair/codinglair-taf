# ADR-024: Provision LocalStack Resources Through the Environment Provider and Enforce Ownership

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

#### Context

LocalStack enables local qualification of EventBridge and SQS and may support additional AWS services later. Treating LocalStack as controller-owned infrastructure would couple service operations to provisioning and could allow cleanup to affect externally managed resources. The existing architecture already separates environment provisioning from controller execution.

#### Decision

• Implement a LocalStack AWS environment provider that can use a Testcontainers-managed LocalStack container or connect to a declared external LocalStack endpoint.

• Represent provisioned AWS resources with typed resource descriptors and an immutable ownership manifest containing resource type, logical name, physical identifier, owner session or run, creation source, cleanup policy, and dependency order.

• Support EPHEMERAL test-owned mode and EXTERNAL operator-owned mode. Controllers may use resources in either mode but may not create or destroy them implicitly.

• Allow the environment provider in EPHEMERAL mode to create event buses, rules, SQS queues, dead-letter queues, queue policies, targets, and redrive policies. Clean up only resources recorded as test-owned, in reverse dependency order.

• In EXTERNAL mode, require configured resource identifiers and authorization, perform non-mutating preflight checks by default, and prohibit implicit resource creation, policy changes, purge operations, and deletion.

• Model AWS services through an extensible AwsServiceEnvironmentContributor SPI. Each future service contributes provisioning, readiness, diagnostics, and cleanup behavior without changing controller or TestSession contracts.

• Maintain a documented LocalStack compatibility profile and run representative authorized-AWS qualification when emulator differences affect a supported behavior.

#### Consequences

• LocalStack remains a replaceable environment implementation instead of becoming the AWS abstraction.

• Future AWS capabilities can reuse container lifecycle, endpoint publication, ownership, and diagnostics.

• Provisioning code must maintain dependency-aware cleanup and prove that external resources survive success and failure paths.

• Exact LocalStack and AWS SDK patch versions remain release-managed pins rather than architecture constants.

#### Alternatives Considered

• Controller-managed provisioning was rejected because it mixes test interaction with infrastructure lifecycle and violates operator ownership.

• LocalStack-specific controller APIs were rejected because they would prevent transparent execution against authorized AWS endpoints.

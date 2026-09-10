# ADR 024 Provision LocalStack Resources Through the Environment Provider and Enforce Ownership

- Status: Accepted
- Date: 2026-09-09
- Target release: codinglair-taf 1.1.0

## Context

LocalStack provides local EventBridge and SQS behavior and can support future AWS capabilities. Controllers must not own infrastructure lifecycle or delete resources supplied by operators, Kind, AWS, or infrastructure as code.

## Decision

A LocalStack AWS environment provider will support a Testcontainers-managed container and a declared external endpoint. Managed provisioning will record typed resource descriptors in an immutable ownership manifest. The manifest identifies resource type, logical and physical identifiers, owner session or run, creation source, cleanup policy, and dependency order.

`EPHEMERAL` mode may create and clean test-owned buses, rules, queues, DLQs, queue policies, targets, and redrive policies. `EXTERNAL` mode requires configured resources and performs non-mutating preflight by default. Controllers may use resources in both modes but cannot provision or destroy them implicitly. Cleanup operates only on test-owned manifest entries and follows reverse dependency order.

Future services will contribute provisioning, readiness, diagnostics, and cleanup through an `AwsServiceEnvironmentContributor` SPI. LocalStack remains an environment implementation, not the framework's AWS abstraction. A versioned compatibility profile will document supported behavior and material differences from AWS.

## Consequences

- Provisioning remains separate from controller execution.
- Future LocalStack-supported services can reuse lifecycle, endpoint, ownership, and diagnostic infrastructure.
- Cleanup tests must prove that external resources survive success and failure paths.
- Exact SDK and emulator patch versions remain release-managed pins.

## Alternatives Considered

- Controller-managed provisioning was rejected because it violates separation and ownership controls.
- LocalStack-specific controller APIs were rejected because they prevent portable execution against AWS.

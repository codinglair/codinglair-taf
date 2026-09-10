# Architecture Decision Records

This directory contains accepted architecture decisions for the Codinglair TAF product family.

## ADR lifecycle

- **Proposed:** Under review and not yet authoritative
- **Accepted:** Governs implementation
- **Deprecated:** Retained for history but no longer recommended
- **Superseded:** Replaced by a later ADR
- **Rejected:** Considered but not adopted

Accepted ADRs are immutable except for corrections that do not change the decision. A material change requires a new ADR that supersedes the previous record.

## Index

| ADR | Decision | Status |
|---|---|---|
| [ADR-001](ADR-001_separate-runtime-mcp-server-and-quality-intelligence-products.md) | Separate Runtime, MCP Server, and Quality Intelligence products | Accepted |
| [ADR-002](ADR-002_use-spring-boot-composition-with-a-lightweight-framework-core.md) | Use Spring Boot composition with a lightweight framework core | Accepted |
| [ADR-003](ADR-003_approve-the-testcontroller-breaking-redesign.md) | Approve the TestController breaking redesign | Accepted |
| [ADR-004](ADR-004_use-a-typed-named-controller-registry-within-testsession.md) | Use a typed named controller registry within TestSession | Accepted |
| [ADR-005](ADR-005_keep-reporting-vendor-neutral-and-isolate-allure-and-aspectj.md) | Keep reporting vendor-neutral and isolate Allure and AspectJ | Accepted |
| [ADR-006](ADR-006_keep-testng-and-cucumber-runner-integrations-independent.md) | Keep TestNG and Cucumber test models separate while using TestNG XML orchestration | Accepted |
| [ADR-007](ADR-007_separate-environment-provisioning-from-controllers-and-support-testcontainers.md) | Separate environment provisioning from controllers and support Testcontainers | Accepted |
| [ADR-008](ADR-008_use-mongodb-as-the-recommended-test-definition-store-behind-an-spi.md) | Use MongoDB as the recommended test-definition store behind an SPI | Accepted |
| [ADR-009](ADR-009_expose-coarse-grained-mcp-workflows-with-asynchronous-jobs.md) | Expose coarse-grained MCP workflows with asynchronous jobs | Accepted |
| [ADR-010](ADR-010_write-generated-code-as-proposed-working-tree-changes-without-autonomous-commits.md) | Write generated code as proposed working-tree changes without autonomous commits | Accepted |
| [ADR-011](ADR-011_keep-secrets-outside-model-and-mcp-context.md) | Keep secrets outside model and MCP context | Accepted |
| [ADR-012](ADR-012_start-the-control-plane-as-a-modular-monolith-with-isolated-execution-workers.md) | Start the control plane as a modular monolith with isolated execution workers | Accepted |
| [ADR-013](ADR-013_implement-android-appium-first-and-preserve-an-ios-provider-boundary.md) | Implement Android Appium first and preserve an iOS provider boundary | Accepted |
| [ADR-014](ADR-014_separate-framework-self-tests-from-consumer-test-execution-and-tier-ci-verification.md) | Separate framework self-tests from consumer test execution and tier CI verification | Accepted |
| [ADR-015](ADR-015_provide-an-incremental-functional-kind-reference-deployment.md) | Provide an incremental functional Kind reference deployment | Accepted |
| [ADR-016](ADR-016_defer-detailed-quality-intelligence-assignments-until-runtime-and-mcp-stabilize.md) | Defer detailed Quality Intelligence assignments until Runtime and MCP stabilize | Accepted |
| [ADR-017](ADR-017_java-25-platform-baseline.md) | Java 25 platform baseline existing project ADR | Accepted |
| [ADR-018](ADR-018_package-optional-controllers-as-capability-specific-modules.md) | Package optional controllers as capability-specific modules | Accepted |
| [ADR-019](ADR-019_standardize-consumer-project-blueprints-and-test-design-patterns.md) | Standardize consumer project blueprints and test design patterns | Accepted |
| [ADR-020](ADR-020_enforce-conformance-for-agent-generated-and-migrated-test-assets.md) | Enforce conformance for agent-generated and migrated test assets | Accepted |
| [ADR-021](ADR-021_normalize-plaintext-secrets-at-an-explicit-test-data-authoring-boundary.md) | Normalize plaintext secrets at an explicit test-data authoring boundary | Accepted |
| [ADR-022](ADR-022_separate-taf-context-and-sut-data-planes-and-govern-versioned-database-lifecycles.md) | Separate TAF context and SUT data planes and govern versioned database lifecycles | Accepted |
| [ADR-023](ADR-023_use-separate-eventbridge-and-sqs-controllers-in-one-aws-capability-module.md) | Use Separate EventBridge and SQS Controllers in One AWS Capability Module) | Accepted |
| [ADR-024](ADR-024_provision-localstack-resources-through-the-environment-provider-and-enforce-ownership.md) | Provision LocalStack Resources Through the Environment Provider and Enforce Ownership| Accepted |
| [ADR-025](ADR-025_require-non-destructive-sqs-isolation-and-verify-eventbridge-routing-through-sqs.md) | Require Non Destructive SQS Isolation and Verify EventBridge Routing Through SQS| Accepted |

## Authoring rules

1. Use the next sequential number.
2. Describe the forces and constraints, not only the chosen technology.
3. Record rejected alternatives and meaningful consequences.
4. Link superseded and superseding decisions in both records.
5. Update architecture tests, compatibility matrices, and implementation assignments when a decision becomes accepted.

# Codinglair TAF 1.3.0 ADR Package

Source: SAD 1.13, based on SAD 1.12 and BRD 1.4. Prepared September 30, 2026.

ADR-031–035 are new proposed decisions. ADR-013 is inherited with partial-supersession disposition. ADR-001–030 reproduce the decision content available in the supplied SAD; this package does not claim recovery of richer historical standalone ADR files or invent their metadata.

| Record | Revision disposition | File |
|---|---|---|
| ADR-001 | Inherited | [ADR-001_separate-runtime-mcp-server-and-quality-intelligence-products.md](ADR-001_separate-runtime-mcp-server-and-quality-intelligence-products.md) |
| ADR-002 | Inherited | [ADR-002_use-spring-boot-composition-with-a-lightweight-framework-core.md](ADR-002_use-spring-boot-composition-with-a-lightweight-framework-core.md) |
| ADR-003 | Inherited | [ADR-003_approve-the-testcontroller-breaking-redesign.md](ADR-003_approve-the-testcontroller-breaking-redesign.md) |
| ADR-004 | Inherited | [ADR-004_use-a-typed-named-controller-registry-within-testsession.md](ADR-004_use-a-typed-named-controller-registry-within-testsession.md) |
| ADR-005 | Inherited | [ADR-005_keep-reporting-vendor-neutral-and-isolate-allure-and-aspectj.md](ADR-005_keep-reporting-vendor-neutral-and-isolate-allure-and-aspectj.md) |
| ADR-006 | Inherited | [ADR-006_keep-testng-and-cucumber-runner-integrations-independent.md](ADR-006_keep-testng-and-cucumber-runner-integrations-independent.md) |
| ADR-007 | Inherited | [ADR-007_separate-environment-provisioning-from-controllers-and-support-testcontainers.md](ADR-007_separate-environment-provisioning-from-controllers-and-support-testcontainers.md) |
| ADR-008 | Inherited | [ADR-008_use-mongodb-as-the-recommended-test-definition-store-behind-an-spi.md](ADR-008_use-mongodb-as-the-recommended-test-definition-store-behind-an-spi.md) |
| ADR-009 | Inherited | [ADR-009_expose-coarse-grained-mcp-workflows-with-asynchronous-jobs.md](ADR-009_expose-coarse-grained-mcp-workflows-with-asynchronous-jobs.md) |
| ADR-010 | Inherited | [ADR-010_write-generated-code-as-proposed-working-tree-changes-without-autonomous-commits.md](ADR-010_write-generated-code-as-proposed-working-tree-changes-without-autonomous-commits.md) |
| ADR-011 | Inherited | [ADR-011_keep-secrets-outside-model-and-mcp-context.md](ADR-011_keep-secrets-outside-model-and-mcp-context.md) |
| ADR-012 | Inherited | [ADR-012_start-the-control-plane-as-a-modular-monolith-with-isolated-execution-workers.md](ADR-012_start-the-control-plane-as-a-modular-monolith-with-isolated-execution-workers.md) |
| ADR-013 | Inherited — partially superseded for Apple scope | [ADR-013_implement-android-appium-first-and-preserve-an-ios-provider-boundary.md](ADR-013_implement-android-appium-first-and-preserve-an-ios-provider-boundary.md) |
| ADR-014 | Inherited | [ADR-014_separate-framework-self-tests-from-consumer-test-execution-and-tier-ci-verification.md](ADR-014_separate-framework-self-tests-from-consumer-test-execution-and-tier-ci-verification.md) |
| ADR-015 | Inherited | [ADR-015_provide-an-incremental-functional-kind-reference-deployment.md](ADR-015_provide-an-incremental-functional-kind-reference-deployment.md) |
| ADR-016 | Inherited | [ADR-016_defer-detailed-quality-intelligence-assignments-until-runtime-and-mcp-stabilize.md](ADR-016_defer-detailed-quality-intelligence-assignments-until-runtime-and-mcp-stabilize.md) |
| ADR-017 | Inherited | [ADR-017_java-25-platform-baseline.md](ADR-017_java-25-platform-baseline.md) |
| ADR-018 | Inherited | [ADR-018_package-optional-controllers-as-capability-specific-modules.md](ADR-018_package-optional-controllers-as-capability-specific-modules.md) |
| ADR-019 | Inherited | [ADR-019_standardize-consumer-project-blueprints-and-test-design-patterns.md](ADR-019_standardize-consumer-project-blueprints-and-test-design-patterns.md) |
| ADR-020 | Inherited | [ADR-020_enforce-conformance-for-agent-generated-and-migrated-test-assets.md](ADR-020_enforce-conformance-for-agent-generated-and-migrated-test-assets.md) |
| ADR-021 | Inherited | [ADR-021_normalize-plaintext-secrets-at-an-explicit-test-data-authoring-boundary.md](ADR-021_normalize-plaintext-secrets-at-an-explicit-test-data-authoring-boundary.md) |
| ADR-022 | Inherited | [ADR-022_separate-taf-context-and-sut-data-planes-and-govern-versioned-database-lifecycles.md](ADR-022_separate-taf-context-and-sut-data-planes-and-govern-versioned-database-lifecycles.md) |
| ADR-023 | Inherited | [ADR-023_use-separate-eventbridge-and-sqs-controllers-in-one-aws-capability-module.md](ADR-023_use-separate-eventbridge-and-sqs-controllers-in-one-aws-capability-module.md) |
| ADR-024 | Inherited | [ADR-024_provision-localstack-resources-through-the-environment-provider-and-enforce-ownership.md](ADR-024_provision-localstack-resources-through-the-environment-provider-and-enforce-ownership.md) |
| ADR-025 | Inherited | [ADR-025_require-non-destructive-sqs-isolation-and-verify-eventbridge-routing-through-sqs.md](ADR-025_require-non-destructive-sqs-isolation-and-verify-eventbridge-routing-through-sqs.md) |
| ADR-026 | Inherited | [ADR-026_publish-capability-oriented-starters-with-provider-specific-messaging-starters.md](ADR-026_publish-capability-oriented-starters-with-provider-specific-messaging-starters.md) |
| ADR-027 | Inherited | [ADR-027_compose-projects-from-a-common-blueprint-and-capability-contributions.md](ADR-027_compose-projects-from-a-common-blueprint-and-capability-contributions.md) |
| ADR-028 | Inherited | [ADR-028_include-secrets-api-and-an-explicitly-activated-local-provider-in-starters.md](ADR-028_include-secrets-api-and-an-explicitly-activated-local-provider-in-starters.md) |
| ADR-029 | Inherited | [ADR-029_distribute-one-mcp-image-with-stdio-and-streamable-http-profiles.md](ADR-029_distribute-one-mcp-image-with-stdio-and-streamable-http-profiles.md) |
| ADR-030 | Inherited | [ADR-030_gate-releases-with-external-starter-and-blueprint-conformance.md](ADR-030_gate-releases-with-external-starter-and-blueprint-conformance.md) |
| ADR-031 | New — proposed | [ADR-031_extend-the-existing-mobile-module-and-starter-with-xcuitest-strategies.md](ADR-031_extend-the-existing-mobile-module-and-starter-with-xcuitest-strategies.md) |
| ADR-032 | New — proposed | [ADR-032_consume-external-apple-infrastructure-with-topology-aware-preflight.md](ADR-032_consume-external-apple-infrastructure-with-topology-aware-preflight.md) |
| ADR-033 | New — proposed | [ADR-033_preserve-platform-semantics-and-isolate-apple-sessions.md](ADR-033_preserve-platform-semantics-and-isolate-apple-sessions.md) |
| ADR-034 | New — proposed | [ADR-034_integrate-apple-evidence-and-consumer-surfaces-through-existing-contracts.md](ADR-034_integrate-apple-evidence-and-consumer-surfaces-through-existing-contracts.md) |
| ADR-035 | New — proposed | [ADR-035_qualify-apple-compatibility-with-the-existing-android-verification-approach.md](ADR-035_qualify-apple-compatibility-with-the-existing-android-verification-approach.md) |

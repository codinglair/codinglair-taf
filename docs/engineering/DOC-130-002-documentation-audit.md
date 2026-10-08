# DOC-130-002 repository documentation audit

Date: 2026-10-07. Inventory authority: tracked files from `git ls-files`, plus the
non-Markdown public surfaces listed below. The checkout contained a release-owner-staged root
revision change to 1.3.0 and a staged REL-130-001 readiness record; this assignment does not claim
ownership of, revert, or rewrite those changes.

## Method and evidence boundary

The inventory was reviewed in bounded root, module/example, architecture/evidence, operations, and
reference batches. Current claims were compared with Runtime/MCP source, starter and capability
manifests, the standalone Apple consumer, SEC-130-001 and predecessor handoffs, ADR-031–035, and
the immutable VER-130-002 candidate-2 record. “Reviewed-current/no change” means the surface was
opened or included in its bounded semantic/search batch and no current guidance conflicted with
the implemented increment. Historical records remain factual for their recorded candidate or
release; their Android-only, pending, or 1.2.0 statements are not current guidance.

Version-marker categories distinguish current root-revision values from target labels, exact tool
locks, executed tuples, and historical release facts. Generated/vendor dependencies and binary
documentation were checked with `git ls-files`; no tracked vendor documentation or binary
documentation was present. The two sample report text files are product fixtures, not user
guidance, and are explicitly excluded below. The visible private SAD was consulted for the named
Apple sections but remains untracked/private and is not reproduced or added.

## Tracked prose/document surfaces

| Path | Audience/status | Implemented source/evidence | Finding | Version-marker category | Disposition | Verification |
| --- | --- | --- | --- | --- | --- | --- |
| `CHANGELOG.md` | Public root | Source + ADR-031–035 + candidate 2 | Current drift resolved | Historical marker removed | Updated | Docs contract + links/grep |
| `CONTRIBUTING.md` | Public root | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `README.md` | Public root | Source + ADR-031–035 + candidate 2 | Current drift resolved | Current marker or none | Updated | Docs contract + links/grep |
| `SECURITY.md` | Public root | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `blueprints/playwright-consumer-v1/README.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `blueprints/playwright-consumer-v1/template/README.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `build-support/local/README.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `codinglair-taf-runtime/codinglair-taf-reporting-allure/src/main/resources/reports/sample-business-report.txt` | Maintainer | Owning source/contracts | Static report fixture; not guidance | Current marker or none | Excluded-with-reason | Ownership inspection |
| `codinglair-taf-runtime/codinglair-taf-reporting-allure/src/main/resources/reports/sample-technical-report.txt` | Maintainer | Owning source/contracts | Static report fixture; not guidance | Current marker or none | Excluded-with-reason | Ownership inspection |
| `codinglair-taf-runtime/codinglair-taf-runner-cucumber/README.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `codinglair-taf-runtime/codinglair-taf-runtime-core/README.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `codinglair-taf-runtime/taf-api-rest/README.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `codinglair-taf-runtime/taf-api-soap/README.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `codinglair-taf-runtime/taf-consumer-conformance/README.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `codinglair-taf-runtime/taf-contracts/README.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `codinglair-taf-runtime/taf-data-migration/README.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `codinglair-taf-runtime/taf-database/README.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `codinglair-taf-runtime/taf-environments/README.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `codinglair-taf-runtime/taf-file/README.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `codinglair-taf-runtime/taf-mobile-appium/README.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `codinglair-taf-runtime/taf-secrets-api/README.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `codinglair-taf-runtime/taf-secrets-local/README.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `codinglair-taf-runtime/taf-test-definitions-mongodb/README.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `codinglair-taf-runtime/taf-test-definitions/README.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `codinglair-taf-runtime/taf-virtualization-wiremock/README.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `codinglair-taf-runtime/taf-web-playwright/README.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `containers/android-emulator/README.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `containers/android-emulator/google/README.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `demos/playwright-sauce-demo/README.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/architecture/adrs/ADR-001_separate-runtime-mcp-server-and-quality-intelligence-products.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-002_use-spring-boot-composition-with-a-lightweight-framework-core.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-003_approve-the-testcontroller-breaking-redesign.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-004_use-a-typed-named-controller-registry-within-testsession.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-005_keep-reporting-vendor-neutral-and-isolate-allure-and-aspectj.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-006_keep-testng-and-cucumber-runner-integrations-independent.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-007_separate-environment-provisioning-from-controllers-and-support-testcontainers.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-008_use-mongodb-as-the-recommended-test-definition-store-behind-an-spi.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-009_expose-coarse-grained-mcp-workflows-with-asynchronous-jobs.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-010_write-generated-code-as-proposed-working-tree-changes-without-autonomous-commits.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-011_keep-secrets-outside-model-and-mcp-context.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-012_start-the-control-plane-as-a-modular-monolith-with-isolated-execution-workers.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-013_implement-android-appium-first-and-preserve-an-ios-provider-boundary.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-014_separate-framework-self-tests-from-consumer-test-execution-and-tier-ci-verification.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-015_provide-an-incremental-functional-kind-reference-deployment.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-016_defer-detailed-quality-intelligence-assignments-until-runtime-and-mcp-stabilize.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-017_java-25-platform-baseline.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-018_package-optional-controllers-as-capability-specific-modules.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-019_standardize-consumer-project-blueprints-and-test-design-patterns.md` | Evidence/decision | Source + ADR-031–035 + candidate 2 | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-020_enforce-conformance-for-agent-generated-and-migrated-test-assets.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-021_normalize-plaintext-secrets-at-an-explicit-test-data-authoring-boundary.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-022_separate-taf-context-and-sut-data-planes-and-govern-versioned-database-lifecycles.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-023_use-separate-eventbridge-and-sqs-controllers-in-one-aws-capability-module.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-024_provision-localstack-resources-through-the-environment-provider-and-enforce-ownership.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-025_require-non-destructive-sqs-isolation-and-verify-eventbridge-routing-through-sqs.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-026_publish-capability-oriented-starters-with-provider-specific-messaging-starters.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-027_compose-projects-from-a-common-blueprint-and-capability-contributions.md` | Evidence/decision | Source + ADR-031–035 + candidate 2 | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-028_include-secrets-api-and-an-explicitly-activated-local-provider-in-starters.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-029_distribute-one-mcp-image-with-stdio-and-streamable-http-profiles.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-030_gate-releases-with-external-starter-and-blueprint-conformance.md` | Evidence/decision | Source + ADR-031–035 + candidate 2 | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-031_extend-the-existing-mobile-module-and-starter-with-xcuitest-strategies.md` | Evidence/decision | Source + ADR-031–035 + candidate 2 | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-032_consume-external-apple-infrastructure-with-topology-aware-preflight.md` | Evidence/decision | Source + ADR-031–035 + candidate 2 | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-033_preserve-platform-semantics-and-isolate-apple-sessions.md` | Evidence/decision | Source + ADR-031–035 + candidate 2 | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-034_integrate-apple-evidence-and-consumer-surfaces-through-existing-contracts.md` | Evidence/decision | Source + ADR-031–035 + candidate 2 | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/ADR-035_qualify-apple-compatibility-with-the-existing-android-verification-approach.md` | Evidence/decision | Source + ADR-031–035 + candidate 2 | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/adrs/README.md` | Evidence/decision | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/architecture/consumer-conformance-checklist.md` | Maintainer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/architecture/consumer-project-blueprint.md` | Maintainer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/architecture/decisions/AWS-110-001-dependency-compatibility.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/decisions/MOB-001-appium-java25-compatibility.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/architecture/mcp/GATE-006-contract-snapshots.md` | Maintainer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/architecture/mcp/MCP-001-contract-versioning.md` | Maintainer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/architecture/mcp/MCP-002-job-persistence.md` | Maintainer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/architecture/mcp/MCP-003-security-enforcement.md` | Maintainer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/architecture/mcp/MCP-004-isolated-local-worker.md` | Maintainer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/architecture/mcp/MCP-006-coarse-grained-tools.md` | Maintainer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/architecture/mcp/MCP-007-scaffold-workflow.md` | Maintainer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/architecture/mcp/MCP-008-stdio-transport.md` | Maintainer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/architecture/mcp/MCP-009-streamable-http-security.md` | Maintainer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/architecture/solution-architecture.md` | Maintainer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/assignments/MOB-003-android-smoke-evidence.txt` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/assignments/MOB-003-approval-package.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/assignments/MOB-003-github-qualification-evidence.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/assignments/MOB-003-handoff.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/assignments/MOB-003-license-obligation-report.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/assignments/Release_1.3.0_readiness.md` | Evidence/decision | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/decisions/MOB-003-reference-qualification-approval.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/engineering/apple-130-contract-and-qualification-plan.md` | Evidence/decision | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/engineering/compatibility-matrix.md` | Maintainer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/engineering/kiss-refactoring-discovery.md` | Maintainer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/operations/android-emulator-image-approval-policy.md` | Operator | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/operations/aws-capability-ci.md` | Operator | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/operations/container-images.md` | Operator | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/operations/github-required-checks.md` | Operator | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/operations/jenkins-and-gitlab-reference-pipelines.md` | Operator | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/operations/kind-reference-deployment.md` | Operator | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/operations/mcp-container-deployment.md` | Operator | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/operations/mcp-kubernetes-reference.md` | Operator | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/operations/nightly-and-release-verification.md` | Operator | Source + ADR-031–035 + candidate 2 | Current drift resolved | Current marker or none | Updated | Docs contract + links/grep |
| `docs/operations/release-license-compliance.md` | Operator | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/operations/release-packaging.md` | Operator | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/qualification/apple/VER-130-002-candidate-1.md` | Evidence/decision | Source + ADR-031–035 + candidate 2 | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/qualification/apple/VER-130-002-candidate-2.md` | Evidence/decision | Source + ADR-031–035 + candidate 2 | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/quick-start-capability-matrix.md` | Consumer | Source + ADR-031–035 + candidate 2 | Current drift resolved | Current marker or none | Updated | Docs contract + links/grep |
| `docs/quick-start.md` | Consumer | Source + ADR-031–035 + candidate 2 | Current drift resolved | Current marker or none | Updated | Docs contract + links/grep |
| `docs/reference/README.md` | Consumer | Source + ADR-031–035 + candidate 2 | Current drift resolved | Current marker or none | Updated | Docs contract + links/grep |
| `docs/reference/android-appium-setup.md` | Consumer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/reference/apple-appium-compatibility-1.3.0.md` | Consumer | Source + ADR-031–035 + candidate 2 | Current drift resolved | Current marker or none | Updated | Docs contract + links/grep |
| `docs/reference/apple-appium-consumer-guide.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/reference/apple-appium-operations.md` | Operator | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/reference/apple-evidence-and-reporting.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/reference/apple-transport-security.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/reference/aws-messaging.md` | Consumer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/reference/blueprint-composition-contract.md` | Consumer | Source + ADR-031–035 + candidate 2 | Current drift resolved | Current marker or none | Updated | Docs contract + links/grep |
| `docs/reference/consumer-dependencies.md` | Consumer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/reference/extension-spi.md` | Consumer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/reference/installation-and-prerequisites.md` | Consumer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/reference/maintainer-guide.md` | Consumer | Owning source/contracts | Current drift resolved | Current marker or none | Updated | Docs contract + links/grep |
| `docs/reference/mcp-and-security.md` | Consumer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/reference/mcp-image-runtime-contract.md` | Consumer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/reference/messaging-jms-ems-compatibility.md` | Consumer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/reference/operations-and-troubleshooting.md` | Operator | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/reference/runtime-and-configuration.md` | Consumer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/reference/runtime-failure-classification-and-history.md` | Consumer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/reference/starter-dependency-contract.md` | Consumer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/reference/testng-cucumber-separation.md` | Consumer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/reference/training-session-guide.md` | Consumer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/releases/1.1.0-release-candidate.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/releases/1.1.0-release-notes.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/releases/1.2.0-release-candidate.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/releases/1.2.0-release-notes.md` | Evidence/decision | Owning source/contracts | Immutable historical context retained | Immutable literal / none | Historical-preserved | Semantic review; unchanged |
| `docs/reporting/single-file-allure.md` | Maintainer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/security/mcp-authorization-matrix.md` | Maintainer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/security/mcp-threat-model.md` | Maintainer | Owning source/contracts | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `examples/apple-appium-consumer/APPLE.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `examples/apple-appium-consumer/README.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `qualification/apple-simulator/README.md` | Evidence/decision | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `qualification/apple-simulator/web/index.html` | Evidence/decision | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `release/consumer-smoke/README.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `release/consumer-smoke/aws-messaging/README.md` | Consumer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `taf-mcp-server/taf-mcp-tools/src/main/resources/mobile-blueprint/APPLE.md` | Maintainer | Source + ADR-031–035 + candidate 2 | No Apple/security/version drift found | Current marker or none | Reviewed-current/no change | Batch semantic/link review |
| `docs/engineering/DOC-130-002-documentation-audit.md` | Maintainer/current | This assignment and tracked inventory | Audit added; no open rows | No current-version literal | Updated | Inventory count and no-TODO check |
| `docs/assignments/handoffs/DOC-130-002-handoff.md` | Maintainer/current | This audit and executed commands | Completion record added | No current-version literal | Updated | Handoff review |
| `docs/assignments/DOC-130-002-content-readiness.md` | Release owner/current | Audit and REL-130-001 historical assessment | Superseding content-only disposition added | Owner-controlled revision stated literally | Updated | Content-readiness review |

## Non-prose user-facing surfaces

| Surface | Audience/status | Implemented source/evidence | Finding | Version-marker category | Disposition | Verification |
| --- | --- | --- | --- | --- | --- | --- |
| `.github/workflows/ver-130-002-apple-simulator.yml` | Operator/current | Candidate-2 run 37695433922 | Real native/hybrid/Safari simulator entry point is current | Exact executed/tool values | Reviewed-current/no change | Workflow/source inspection |
| `.github/workflows/{pull-request,nightly,release,verification-matrix}.yml` | Operator/current | Workflow graph and predecessor gates | Apple lane is intentionally separate and owner-verified | Root-derived/current or none | Reviewed-current/no change | Workflow/source inspection |
| Other tracked `.github/workflows/*.yml`, `.gitlab-ci.yml`, scripts and container/deploy manifests | Operator/current | Owning operations guides and contract tests | No stale Apple promise or conflicting readiness rule found | Root-derived/current or exact operational input | Reviewed-current/no change | Semantic search and owning tests |
| `docs/reference/{consumer-snippet-manifest-v1,starter-capability-manifest-v1}.json` | Consumer contract/current | Apple guide, starter catalog and conformance tests | Apple documents/capability are registered | Schema/catalog version, not artifact version | Reviewed-current/no change | JSON/docs contracts |
| `docs/architecture/schemas/*.json` and Runtime blueprint schema | Consumer contract/current | Blueprint engine and schema tests | Apple selection remains additive within existing schema | Schema version, not artifact version | Reviewed-current/no change | Schema compatibility gate |
| MCP v1 catalog and schema JSON resources | MCP client/current | MCP-130-001 handoff and contract tests | `mobile.apple`, readiness and image tuple remain registered | Contract/image tuple literals | Reviewed-current/no change | MCP contract/docs profile |
| Apple consumer YAML, qualification YAML/HTML, and mobile-blueprint `APPLE.md` | Consumer/fixture/current | Standalone consumer and candidate-2 run | Native, packaged, hybrid and Safari sources match guidance | Candidate inputs; no managed marker | Reviewed-current/no change | Golden-source/docs contract |
| Public Java package/API documentation affected by Apple and resource authorization | API consumer/current | Mobile Appium/core, Runtime security, MCP contracts source and Javadocs | Responsibility names and additive contracts agree; no public signature changed here | Javadoc source; no artifact literal | Reviewed-current/no change | Source/Javadoc and docs-profile verification |
| Test-only contract/config fixtures, deploy YAML, Dockerfiles and shell/PowerShell usage comments not named above | Maintainer/internal | Owning tests and operations documents | Not independent guidance; no conflicting current claim found | Exact fixture inputs or root-derived | Excluded-with-reason | Tracked inventory and semantic search |

## Resolved findings

- Root README, capability matrix, Quick Start and reference index now describe both
  Android/UiAutomator2 and Apple/XCUITest without overstating provider or physical-device coverage.
- The blueprint contract now treats iPad selection as supported and limits
  `SCF_UNSUPPORTED_IOS` to its historical 1.2.0 meaning.
- The operations guide records the dedicated hosted simulator lane and its manual, separate
  disposition. Pending hosted execution is not an implementation/review blocker.
- The current Apple compatibility summary links the immutable passing candidate-2 evidence and
  retains explicit physical-device/iPad/provider/tuple limits.
- `CHANGELOG.md` has substantive Unreleased Added/Changed/Security/limitations entries. Its dated
  1.2.0 heading remains literal and its historical Apple limitation remains unchanged.
- The maintainer guide now requires this release-wide inventory and defines marker/historical
  ownership. The documentation contract rejects recurrence of the confirmed stale Apple claims.
- SEC-130-001 resource authorization, redirect/destination trust, bounded transport, reference-only
  authentication and sanitized evidence are represented as new Apple increment behavior, not
  conflated with secret resolution.

## Retained search hits

Android-only statements in ADR-013, 1.2.0 release notes, old handoffs and candidate-1 evidence are
historical and retained. Android setup/module prose remains context-specific rather than a claim
that Apple is absent repository-wide. Candidate-2’s 1.2.0 artifact tuple is an executed fact.
`NOT RUN` remains in immutable candidate-1/release records and in general instructions explaining
that unavailable evidence is not a pass. None is an open current-document finding.

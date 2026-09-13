# AWS messaging dependency compatibility

- AWS SDK for Java `2.42.15` is pinned through its BOM. The module consumes only SQS,
  EventBridge, and profile support. The SDK is Apache-2.0 licensed and supports modern JVMs;
  compilation and convergence on the Java 25 reactor are the compatibility gates.
- Testcontainers LocalStack `2.0.5` aligns with the reactor's existing Testcontainers BOM and is
  test-scoped. It is MIT licensed; its transitive Docker client remains outside runtime artifacts.
- `localstack/localstack:4.14.0` is the release-managed test image tag. Linux/amd64 CI resolves it
  immutably as
  `localstack/localstack:4.14.0@sha256:3ebc37595918b8accb852f8048fef2aff047d465167edd655528065b07bc364a`.
  The tag remains the cross-platform Testcontainers default; CI verifies and records the selected
  platform digest. The image is never a runtime-core dependency. INF-110-001 scans the image for
  vulnerabilities, embedded secrets, and licenses, emits a CycloneDX SBOM, and retains provenance.
- The 2026-09-13 approved upgrade from 4.8.1 to 4.14.0 follows a hosted Trivy failure and local
  requalification. Against the current Trivy 0.70.0 databases, the 4.14.0 candidate reported 22
  critical and 290 high vulnerability records, five embedded-secret findings from LocalStack/Moto
  fixtures, and 565 license records. These reports are retained for review rather than flattened
  into a severity-only pass/fail decision. The unmodified image is used only as an ephemeral,
  runner-isolated test appliance and is never distributed with TAF. Scanner or evidence-pipeline
  failure remains blocking; changing the image or digest requires renewed compatibility, security,
  license, and provenance review.
- Main risks are fast AWS SDK release cadence, emulator divergence, and container-image supply
  chain changes. Exact pins, dependency convergence, LocalStack qualification, and authorized-AWS
  contract checks provide the release controls.

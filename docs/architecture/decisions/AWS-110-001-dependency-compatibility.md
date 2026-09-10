# AWS messaging dependency compatibility

- AWS SDK for Java `2.42.15` is pinned through its BOM. The module consumes only SQS,
  EventBridge, and profile support. The SDK is Apache-2.0 licensed and supports modern JVMs;
  compilation and convergence on the Java 25 reactor are the compatibility gates.
- Testcontainers LocalStack `2.0.5` aligns with the reactor's existing Testcontainers BOM and is
  test-scoped. It is MIT licensed; its transitive Docker client remains outside runtime artifacts.
- `localstack/localstack:4.8.1` is the release-managed test image pin. It is never a runtime-core
  dependency. Container execution and vulnerability qualification remain assigned to INF-110-001.
- Main risks are fast AWS SDK release cadence, emulator divergence, and container-image supply
  chain changes. Exact pins, dependency convergence, LocalStack qualification, and authorized-AWS
  contract checks provide the release controls.

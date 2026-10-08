# ADR-017: Java 25 Platform Baseline

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

**Decision**

- Use Java 25 as the project compilation and runtime baseline.
- Pin compatible Spring Boot 4.x, Spring AI 2.x, Maven plugin, test framework, and third-party versions through the parent POM/BOM and Maven Wrapper.
- Do not silently fall back to Java 21 or another JDK when verification requires the approved Java 25 baseline.

**Consequences**

- Framework and CI environments must provide a compatible Java 25 toolchain.
- Dependency and plugin compatibility with Java 25 is part of release verification.

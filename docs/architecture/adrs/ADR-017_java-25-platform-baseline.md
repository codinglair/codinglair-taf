# ADR-017: Java 25 platform baseline

**Status:** Accepted  
**Date:** 2026-08-02  
**Decision owners:** Product Owner and Solution Architecture  
**Scope:** Codinglair TAF product family

## Context

The project is a new standalone codebase using Spring Boot 4.x and a compatible Spring AI 2.x release. It does not require Spring Boot 3.x or Java 21 compatibility. A single pinned toolchain is required to prevent local, CI, and agent verification from silently using different Java versions.

## Decision

- Use Java 25 as the project compilation and runtime baseline.
- Use the latest stable compatible Spring Boot 4.x and Spring AI 2.x releases selected for each release train.
- Pin exact versions through the parent POM/BOM and commit Maven Wrapper.
- Require local, CI, container, Kind, and agent verification environments to use the approved Java 25 toolchain.
- Do not silently fall back to Java 21 or another JDK.

## Consequences

- Dependency and Maven-plugin compatibility with Java 25 is part of release verification.
- Contributors and execution images must provide Java 25.
- A future Java baseline change requires a superseding ADR and compatibility assessment.

## Alternatives considered

- Java 21 — rejected because the approved new-codebase baseline is Java 25.
- Multiple supported Java baselines initially — rejected because it expands the compatibility matrix without a current consumer requirement.
- Unpinned local JDK selection — rejected because it makes builds and agent verification nondeterministic.

## Compliance and verification

- Maven Enforcer/toolchain checks shall fail builds on unsupported Java versions.
- CI and reference deployment manifests shall declare Java 25 explicitly.
- The compatibility matrix shall cover Spring Boot, Spring AI, Maven plugins, TestNG, Cucumber, Playwright, Appium, Testcontainers, AspectJ, and reporting dependencies against Java 25.

## Related documents

- Codinglair TAF and Quality Intelligence SAD v1.5
- Parent POM/BOM
- Maven Wrapper configuration

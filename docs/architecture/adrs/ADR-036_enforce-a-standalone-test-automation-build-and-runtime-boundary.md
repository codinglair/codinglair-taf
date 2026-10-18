# ADR-036: Enforce a Standalone Test-Automation Build and Runtime Boundary

- **Status:** Accepted
- **Date:** 2026-10-18
- **Decision owners:** Codinglair TAF Architecture
- **Applies to:** Official blueprints, scaffolding, MCP generation, consumer conformance and TAF 1.3.2+

## Context

Real consumer generation integrated a TAF test-automation module into the System Under Test (SUT) Maven reactor, modified SUT files and introduced TAF dependencies into the production dependency graph. This violates the functional/E2E automation boundary and can affect SUT compilation, packaging, vulnerability scans and release behavior.

Repository co-location does not imply build composition. Requiring each consumer to provide perfect agent rules is not a sufficient product control; official TAF generation must be safe by construction.

## Decision

Official TAF consumer automation shall be an independent Maven build root and an independent runtime process. It may reside in the same Git repository as the SUT, but:

- it is not registered in the SUT reactor;
- the SUT does not depend on a TAF artifact or import the TAF BOM;
- TAF classes and automation sources are absent from production packages;
- the SUT compiles and packages without executing or resolving the automation build;
- automation communicates with the SUT through supported external interfaces.

The scaffold operation accepts an explicit `automationRoot` and shall:

1. canonicalize the target;
2. reject the SUT build root;
3. reject traversal and symbolic-link escape;
4. require an empty directory or recognized TAF consumer project;
5. write only beneath the canonical automation root;
6. protect ancestor POMs, Gradle files, production sources, application configuration, deployment descriptors and SUT dependency management;
7. verify after generation that no file write or dependency edge crossed the boundary.

The generated project owns its Maven wrapper, POM, configuration, execution and reports. Its descriptor declares `projectType: standalone-test-automation`, the TestNG runner and external SUT interaction model.

A boundary verifier shall fail conformance when a production module resolves `com.codinglair.taf`, contains TAF classes, packages automation output, registers the automation build or changes protected SUT files. The same controls apply to CLI-, MCP-, agent- and human-initiated official generation.

## Consequences

### Positive

- TAF dependencies and vulnerabilities do not enter the SUT production graph through supported integration.
- Automation can upgrade independently of the SUT.
- Same-repository use remains supported without reactor coupling.
- The generator supplies enforceable safety rather than relying only on instructions.

### Negative

- The automation build must be invoked separately in local and CI workflows.
- Shared Java implementation classes cannot be consumed directly across the boundary without a separately governed contract artifact.
- In-process `@SpringBootTest` execution of the SUT is outside this functional/E2E topology.

## Alternatives Considered

### Register test automation as a SUT child module

Rejected. A child module still affects reactor plugins, dependency management, release execution and repository security scans.

### Document the restriction and rely on agent rules

Rejected. This already allowed unsafe generation and does not constitute a product boundary.

### Require a separate Git repository

Not required. A separate build root, dependency graph and runtime provide the necessary isolation while preserving monorepo operation.

## Verification

- Generate automation beside a representative SUT and prove that only `automationRoot` changes.
- Build/package the SUT independently and compare its dependency graph and packaged contents before and after generation.
- Run the automation in a separate JVM against the deployed SUT.
- Verify traversal, symlink escape, SUT-root targeting and ancestor-build modification are rejected.
- Verify a deliberately contaminated SUT fails boundary conformance.


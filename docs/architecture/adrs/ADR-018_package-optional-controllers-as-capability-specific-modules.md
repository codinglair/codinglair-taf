# ADR-018: Package optional controllers as capability-specific modules

**Status:** Accepted  
**Date:** 2026-08-02  
**Decision owners:** Product Owner and Solution Architecture  
**Scope:** Codinglair TAF Runtime and optional controller capabilities

## Context

Controllers introduce materially different vendor dependencies, configuration, lifecycle, infrastructure, and compatibility matrices. Combining Playwright, REST, SOAP, messaging, database, file, observability, and Appium implementations in one controllers artifact would force unused dependencies on consumers and allow optional technologies to leak into Runtime core.

During WEB-001 preparation, Playwright auto-configuration was found in Runtime core and imported a legacy controller implementation. This demonstrated that capability-specific source packages alone are insufficient; Maven and Spring Boot boundaries must enforce the separation.

## Decision

- Keep `taf-core` technology neutral. It contains controller/session contracts, registries, lifecycle, health, artifact, configuration SPI, and other shared Runtime abstractions only.
- Package every optional controller capability in a separate Maven module, including its implementation, vendor SDKs, typed properties, conditional Spring Boot auto-configuration, and capability-specific tests.
- Use capability-oriented modules such as `taf-web-playwright`, `taf-api-rest`, `taf-api-soap`, `taf-messaging-kafka`, `taf-messaging-rabbitmq`, `taf-messaging-jms`, `taf-database`, `taf-file`, `taf-contract`, `taf-observability`, and `taf-mobile-appium`.
- Capability modules depend on public Runtime SPIs and not on one another except through an explicitly approved integration contract.
- Technology-neutral Runtime auto-configuration must not import, instantiate, or require optional capability implementations.
- Consumers select only required capabilities. A BOM may align versions, and a future convenience starter may aggregate dependencies, but neither changes the implementation boundaries or makes optional dependencies mandatory.
- Each module owns its smoke-test profile and technology compatibility verification. For example, WEB-001 creates the Playwright module and browser-smoke profile.

## Consequences

- Runtime core remains usable without Playwright or any other optional controller.
- Consumers avoid unused vendor SDKs and conflicting transitive dependencies.
- Conditional auto-configuration, compatibility matrices, release notes, and tests are capability-specific.
- The repository contains more Maven modules and requires coherent BOM/version management.
- Moving an implementation out of core may require a deliberate migration even when Java packages remain unchanged.

## Alternatives considered

- One combined controllers module — rejected because it forces unrelated optional dependencies and weakens module-boundary enforcement.
- Controller implementations in Runtime core guarded only by conditions — rejected because classes and dependencies still couple core to optional technology.
- Separate source packages in one artifact — rejected because package layout does not enforce dependency or publication boundaries.
- Independently version every capability without a BOM — rejected for the initial release because consumers require a tested coherent version set.

## Compliance and verification

- Architecture tests shall reject capability implementation and vendor SDK dependencies from Runtime core.
- Spring auto-configuration tests shall verify that a capability activates only when its module, required vendor classes, and enabling configuration are present.
- Removing a capability module from a consumer test fixture shall leave Runtime core startup and non-related capabilities functional.
- Every capability module shall provide targeted unit/integration tests and an appropriate smoke-test profile.
- Published dependency analysis shall confirm that selecting one capability does not transitively pull unrelated controller technologies.
- Public migrations shall be recorded in the Legacy Contract Compatibility Matrix.

## Related documents

- Codinglair TAF and Quality Intelligence SAD v1.4
- ADR-002: Use Spring Boot composition with a lightweight framework core
- ADR-003: Approve the TestController breaking redesign
- ADR-005: Keep reporting vendor-neutral and isolate Allure and AspectJ
- Codinglair TAF Engineering and DevOps Implementation Plan
- Legacy Contract Compatibility Matrix, when created

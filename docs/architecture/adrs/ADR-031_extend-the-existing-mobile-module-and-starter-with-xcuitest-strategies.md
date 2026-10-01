# ADR-031: Extend the Existing Mobile Module and Starter with XCUITest Strategies

**Status:** Accepted by the Product Owner for TAF 1.3.0 on 2026-10-01; implementation and architectural review remain required.
**Date:** September 30, 2026  
**Decision owner role:** Solution Architecture; implementation and qualification by Engineering/QA.  
**Related requirements:** FR-MOB-011, FR-MOB-020, FR-MOB-024, FR-MOB-026.  
**Related documents:** BRD 1.4; SAD 1.13 Section 8.4.

#### Context

Apple must be first-class without redesigning core contracts or disrupting Android consumers.

#### Decision

Extend taf-mobile-appium and codinglair-taf-starter-mobile with an Apple/XCUITest strategy using IOSDriver/XCUITestOptions. Preserve Android/UiAutomator2 defaults and public contracts. Normalize iOS/iPadOS selection to the iOS wire platform while retaining family metadata. Share business tasks through platform-specific screens and immutable locators. Do not add an Apple-only consumer starter or couple runtime core to Appium.

#### Consequences

One consumer entry point and one dependency graph cover both platforms. Conditional instance activation and Android regression are required. Apple behavior remains platform-specific.

#### Alternatives Considered

Separate Apple starter/module: unnecessary consumer composition for a shared client dependency. Rewrite core/mobile contracts: violates the approved additive boundary.

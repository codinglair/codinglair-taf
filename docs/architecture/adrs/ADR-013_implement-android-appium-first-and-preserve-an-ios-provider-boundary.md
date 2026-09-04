# ADR-013: Implement Android Appium first and preserve an iOS provider boundary

**Status:** Accepted  
**Date:** 2026-07-24  
**Decision owners:** Product Owner and Solution Architecture  
**Scope:** Codinglair TAF product family

## Context

Mobile automation is required early for a planned consumer project. Full Android, iOS, native, hybrid, local, and cloud support at once would create excessive initial scope, and local iOS requires macOS/Xcode.

## Decision

- Include native Android automation through Appium and UiAutomator2 in the initial deterministic capability phase.
- Support local emulator and authorized physical device, package and preinstalled modes, lifecycle, permissions, gestures, deep links, logs, screenshots, video, and page source.
- Share business-level tasks across web/mobile where useful while keeping platform-specific screens and locators.
- Reserve an XCUITest provider contract and device-cloud adapter boundary.
- Defer full iOS, hybrid/mobile-web, Appium Grid, and broad cloud-device coverage.

## Consequences

- The immediate mobile project is supported.
- The architecture does not assume Android and iOS interaction details are identical.
- Physical-device and emulator verification must be tiered in CI.
- iOS delivery remains dependent on macOS or a cloud provider.

## Alternatives considered

- Defer all mobile — rejected because it does not meet the planned project need.
- Implement Android and iOS fully together — rejected due to scope and infrastructure cost.

## Compliance and verification

- Architecture and dependency tests shall enforce machine-verifiable boundaries.
- The applicable implementation assignments shall include unit, integration, contract, security, and compatibility tests.
- Public contract or schema changes shall update the Legacy Contract Compatibility Matrix and migration guidance.
- Deviations require a superseding ADR or an explicitly approved amendment.

## Related documents

- Test Automation Framework and Quality Intelligence Platform BRD v1.0
- Codinglair TAF and Quality Intelligence SAD v1.0
- Codinglair TAF Engineering and DevOps Implementation Plan v1.0
- Legacy Contract Compatibility Matrix, when created

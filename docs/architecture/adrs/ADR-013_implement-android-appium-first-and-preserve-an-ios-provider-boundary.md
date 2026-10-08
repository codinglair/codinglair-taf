# ADR-013: Implement Android Appium First and Preserve an iOS Provider Boundary

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

**Disposition in SAD 1.13:** Historical initial-release decision. Partially superseded for Apple implementation and Apple hybrid/Safari and generic remote endpoint support by ADR-031–035. Android baseline, shared tasks/platform-specific screens, provisioning separation and tiered verification remain applicable. Appium Grid and broad named-provider certification remain deferred.

**Context** Mobile automation is required early for a planned consumer project. Full Android, iOS, native, hybrid, local, and cloud support at once would create excessive initial scope, and local iOS requires macOS/Xcode.

**Decision**

- Include native Android automation through Appium and UiAutomator2 in the initial deterministic capability phase.
- Support local emulator and authorized physical device, package and preinstalled modes, lifecycle, permissions, gestures, deep links, logs, screenshots, video, and page source.
- Share business-level tasks across web/mobile where useful while keeping platform-specific screens and locators.
- Reserve an XCUITest provider contract and device-cloud adapter boundary.
- Defer full iOS, hybrid/mobile-web, Appium Grid, and broad cloud-device coverage.

**Consequences**

- The immediate mobile project is supported.
- The architecture does not assume Android and iOS interaction details are identical.
- Physical-device and emulator verification must be tiered in CI.
- iOS delivery remains dependent on macOS or a cloud provider.

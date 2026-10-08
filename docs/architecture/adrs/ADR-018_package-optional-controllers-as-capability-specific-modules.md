# ADR-018: Package Optional Controllers as Capability-Specific Modules

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

**Decision**

- Keep Runtime core technology neutral and free of capability implementations, vendor SDKs, and capability-specific configuration.
- Package each optional capability in its own Maven module with its implementation, typed properties, conditional auto-configuration, vendor dependencies, tests, and smoke profile.
- Use modules such as `taf-web-playwright`, `taf-api-rest`, `taf-api-soap`, `taf-messaging-kafka`, `taf-messaging-rabbitmq`, `taf-messaging-jms`, `taf-database`, `taf-file`, `taf-contract`, `taf-observability`, and `taf-mobile-appium`.
- Permit a BOM or convenience starter to align/select modules without merging implementation boundaries or forcing unrelated dependencies.

**Consequences**

- Runtime core works without Playwright or any other optional capability.
- Consumers select only required technologies and their transitive dependencies.
- Each capability owns conditional auto-configuration and compatibility/smoke verification.

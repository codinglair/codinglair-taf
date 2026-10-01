# ADR-005: Keep Reporting Vendor-Neutral and Isolate Allure and AspectJ

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

**Context** Allure is invasive when its annotations and lifecycle APIs appear throughout test and framework code. The legacy design deliberately introduced TestReporter and custom annotations to allow replacement. Controller actions must remain visible in reports without coupling them to Allure.

**Decision**

- Define reporter contracts and annotations in a neutral reporting API module.
- Use TAF-owned annotations for meaningful public controller and project-level actions.
- Confine Allure imports, annotations, lifecycle calls, listeners, and mapping to taf-reporting-allure.
- Confine optional AspectJ weaving to the reporting implementation boundary.
- Route screenshots, traces, payloads, messages, SQL results, and device logs through ArtifactCollector.
- Apply sensitivity classification, size/type policy, sanitization/redaction, retention, hashing, and publication authorization before persistence, reporter attachment, MCP response, or model access.
- Prohibit controllers, listeners, Cucumber plugins, and generated tests from attaching raw evidence directly to a provider.
- Sanitize metadata and content separately. Screenshot/video publication requires an explicit visual-artifact policy because textual redaction cannot sanitize pixels.
- Define nested-step suppression and exactly-once lifecycle/reporting behavior for combined TestNG/Cucumber execution.

**Consequences**

- Allure can be replaced or used alongside another reporter.
- Controllers and generated tests remain vendor neutral.
- Aspect configuration and integration tests remain complex but localized.
- Not every helper method is annotated; only reportable actions are.

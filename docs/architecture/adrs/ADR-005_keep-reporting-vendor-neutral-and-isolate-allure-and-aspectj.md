# ADR-005: Keep reporting vendor-neutral and isolate Allure and AspectJ

**Status:** Accepted  
**Date:** 2026-07-24  
**Last updated:** 2026-08-03  
**Decision owners:** Product Owner and Solution Architecture  
**Scope:** Codinglair TAF product family

## Context

Allure is invasive when its annotations and lifecycle APIs appear throughout test and framework code. The legacy design deliberately introduced TestReporter and custom annotations to allow replacement. Controller actions must remain visible in reports without coupling them to Allure.

## Decision

- Define reporter contracts and annotations in a neutral reporting API module.
- Use TAF-owned annotations for meaningful public controller and project-level actions.
- Confine Allure imports, annotations, lifecycle calls, listeners, and mapping to taf-reporting-allure.
- Confine optional AspectJ weaving to the reporting implementation boundary.
- Route screenshots, traces, payloads, messages, SQL results, and device logs through ArtifactCollector.
- Require `ArtifactCollector` to apply sensitivity classification, size/type policy, sanitization/redaction, retention, hashing, and publication authorization before persistence, reporter attachment, MCP response, or model access.
- Prohibit controllers, runner listeners, Cucumber plugins, and generated tests from attaching raw artifacts directly to Allure or another provider.
- Sanitize metadata and content independently. Screenshot and video publication requires an explicit visual-artifact policy because textual redaction cannot sanitize pixels.
- Define nested-step suppression and exactly-once lifecycle/reporting behavior to avoid duplicated steps, events, cleanup evidence, or sensitive output when TestNG and Cucumber integrations are combined.
- Dedicated Cucumber business reports contain only Cucumber scenarios. Unified technical reports may contain both TestNG and Cucumber results only when intentionally configured and labelled; physical separation uses distinct executions/result directories.
- Reporting listeners are observers only. They may translate framework events and publish sanitized reporting output but must never create, bind, close, replace, or own a `TestSession`.

## Consequences

- Allure can be replaced or used alongside another reporter.
- Controllers and generated tests remain vendor neutral.
- Aspect configuration and integration tests remain complex but localized.
- Not every helper method is annotated; only reportable actions are.
- Artifact capture may succeed while publication is denied or restricted by policy; this is an explicit outcome, not a reason to bypass sanitization.

## Alternatives considered

- Direct Allure annotations — rejected because they hard-couple the framework.
- Remove action reporting — rejected because controller-level evidence is essential.
- Put all AOP in core — rejected because it would preserve vendor/infrastructure coupling.

## Compliance and verification

- Architecture and dependency tests shall reject Allure/provider imports outside approved adapter modules and direct controller-to-reporter artifact paths.
- Tests shall cover sanitization before persistence/publication, secret-safe metadata, denied artifact publication, visual-artifact policy, nested-step suppression, and exactly-once TestNG/Cucumber reporting.
- Tests shall prove that ordinary TestNG tests do not appear in dedicated Cucumber business reports.
- Tests shall prove that enabling or disabling a reporting listener cannot change `TestSession` creation or cleanup counts.
- The applicable implementation assignments shall include unit, integration, contract, security, reporting, and compatibility tests.
- Public contract or schema changes shall update the Legacy Contract Compatibility Matrix and migration guidance.
- Deviations require a superseding ADR or an explicitly approved amendment.

## Revision note

The 2026-08-02 amendment makes the sanitized `ArtifactCollector` path mandatory and closes ambiguity about direct evidence attachment, visual artifacts, and mixed TestNG/Cucumber reporting. The 2026-08-03 amendment explicitly prevents reporting listeners from owning `TestSession` lifecycle.

## Related documents

- Test Automation Framework and Quality Intelligence Platform BRD v1.0
- Codinglair TAF and Quality Intelligence SAD v1.8
- ADR-006: Keep TestNG and Cucumber test models separate while using TestNG XML orchestration
- Codinglair TAF Engineering and DevOps Implementation Plan v1.0
- Legacy Contract Compatibility Matrix, when created

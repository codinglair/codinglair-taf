# ADR-003: Approve the TestController breaking redesign

**Status:** Accepted  
**Date:** 2026-07-24  
**Last updated:** 2026-08-02  
**Decision owners:** Product Owner and Solution Architecture  
**Scope:** Codinglair TAF product family

## Context

The legacy TestController contract contains setup, teardown, and browser-oriented attachment methods. It cannot represent typed health, lifecycle states, multiple artifacts, or technology-neutral controllers. The new repository is not consumed by external projects, so source compatibility provides little value.

## Decision

- Replace the legacy TestController contract with identity, state, initialize, health, artifact collection, and close semantics.
- Use typed subinterfaces for technology-specific behavior.
- Acquire typed and named controller instances lazily through `ControllerRegistry` within `TestSession`.
- Initialization must be explicit, thread-safe, and at most once for each successfully initialized instance. Failed initialization must expose diagnostics, release partial resources, and never leave a falsely ready controller.
- Controller state must reject operation before readiness and after closure. Health information must be typed and secret-safe.
- Controllers return typed evidence to `ArtifactCollector`; they never persist artifacts or call a reporter implementation directly.
- `close()` must be idempotent. `TestSession` closes acquired controllers and registered resources in reverse acquisition order and preserves every cleanup failure.
- Runtime core, runner adapters, controller implementations, auto-configuration, and tests must use the replacement lifecycle exclusively. Retaining legacy setup, teardown, browser-attachment, or single-controller methods as alternate public behavior is non-compliant.
- Do not implement a legacy TestController adapter unless separately approved.
- Update the Sauce demonstration project.
- Create and maintain a Legacy Contract Compatibility Matrix for all public contracts.

## Consequences

- The base contract becomes technology neutral.
- The demo and legacy implementations require migration.
- This is an approved breaking change; other breaks still require review.
- An assignment cannot be marked complete merely because a new type exists alongside an operational legacy lifecycle.

## Alternatives considered

- Source-compatible default methods — rejected because legacy browser assumptions would constrain the new contract.
- Permanent compatibility adapter — rejected because there are no active consumers and it adds maintenance.

## Compliance and verification

- Architecture and dependency tests shall detect Runtime or runner use of the legacy lifecycle and capability-specific dependencies in core.
- Contract tests shall cover identity, permitted state transitions, lazy and concurrent initialization, initialization failure, typed health, artifact handoff, idempotent close, reverse-order cleanup, and aggregated cleanup failure evidence.
- Integration tests shall verify `ControllerRegistry`, `TestSession`, TestNG, and Cucumber execution against the replacement lifecycle.
- Reporting tests shall verify that controller evidence enters the sanitized `ArtifactCollector` path and cannot attach directly to Allure or another provider.
- The applicable implementation assignments shall include unit, integration, contract, concurrency, security, reporting, and compatibility tests.
- Public contract or schema changes shall update the Legacy Contract Compatibility Matrix and migration guidance.
- Deviations require a superseding ADR or an explicitly approved amendment.

## Revision note

The 2026-08-02 amendment removes ambiguity discovered during WEB-001 preparation. ADR-003 compliance requires replacement of the operational legacy lifecycle, not parallel addition of new types while legacy behavior remains active.

## Related documents

- Test Automation Framework and Quality Intelligence Platform BRD v1.0
- Codinglair TAF and Quality Intelligence SAD v1.4
- Codinglair TAF Engineering and DevOps Implementation Plan v1.0
- Legacy Contract Compatibility Matrix, when created

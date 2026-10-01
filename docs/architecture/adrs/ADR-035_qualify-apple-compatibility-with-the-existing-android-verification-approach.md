# ADR-035: Qualify Apple Compatibility with the Existing Android Verification Approach

**Status:** Proposed architecture decision for TAF 1.3.0; approval not asserted.  
**Date:** September 30, 2026  
**Decision owner role:** Solution Architecture; implementation and qualification by Engineering/QA.  
**Related requirements:** FR-MOB-021–022, FR-MOB-026; BRD 33.4; OI-016–017.  
**Related documents:** BRD 1.4; SAD 1.13 Section 8.4.

#### Context

No acceptance application, OS/device or paid infrastructure is mandated, but compatibility and truthful verification must be documented.

#### Decision

Inspect and reuse Android verification layers and cadence, adapting fixtures and checks to Apple. Preserve the current qualified client/Selenium pair if suitable; use 9.5.0/4.34.0 only as an upstream-compatible adjustment candidate. Target Appium 3.x/XCUITest 10.x for the Apple lane and lock exact patches plus Node/macOS/Xcode/WDA/OS provenance before qualification. Record executed and unverified configurations, limitations, artifact availability and BRD traceability. Run external consumer/generator/MCP checks and Android regression. Apple Appium is the sole 1.3.0 scope.

#### Consequences

This SAD establishes architecture and candidates, not completed qualification. Engineering selects available fixtures/infrastructure without adding business procurement obligations. Generic configurability is separate from certified provider coverage. Version locks and result publication are release exit conditions.

#### Alternatives Considered

Mandate a new paid-cloud/device acceptance matrix: contradicts Product Owner decisions. Claim all OS/providers from protocol mocks: overstates evidence. Automatically upgrade every dependency: risks Android compatibility and unrelated scope.

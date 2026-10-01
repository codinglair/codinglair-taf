# ADR-033: Preserve Platform Semantics and Isolate Apple Sessions

**Status:** Accepted by the Product Owner for TAF 1.3.0 on 2026-10-01; implementation and architectural review remain required.
**Date:** September 30, 2026  
**Decision owner role:** Solution Architecture; implementation and qualification by Engineering/QA.  
**Related requirements:** FR-MOB-013, FR-MOB-017–018, FR-MOB-022, FR-MOB-026.  
**Related documents:** BRD 1.4; SAD 1.13 Section 8.4.

#### Context

Apple actions, contexts and reset semantics differ from Android, and shared target/WDA resources can corrupt concurrent sessions.

#### Decision

Implement native/hybrid/Safari operation support through platform strategies. Use bundle IDs and context-aware operations, bounded context discovery and explicit WebView selection. Unsupported Android-specific actions fail clearly. Use normal TestSession ownership, one driver per instance/session, session-local capability/context/evidence state, unique target/WDA/derived-data/MJPEG resources when applicable and external coordination where required. Collect evidence before idempotent teardown; unwind partial initialization and cancellation. Never reset shared infrastructure or replay non-idempotent actions automatically.

#### Consequences

Business tasks remain reusable where practical without promised full parity. Reset and permission limitations are documented. Cleanup is ownership-limited; unknown remote session creation outcomes must be reported. No concurrency minimum is imposed.

#### Alternatives Considered

Universal Android parity layer: creates false semantics. Shared static driver or forced server session override: breaks isolation. Global cleanup: risks unrelated customer sessions.

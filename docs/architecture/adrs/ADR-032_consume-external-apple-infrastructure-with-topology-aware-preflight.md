# ADR-032: Consume External Apple Infrastructure with Topology-Aware Preflight

**Status:** Proposed architecture decision for TAF 1.3.0; approval not asserted.  
**Date:** September 30, 2026  
**Decision owner role:** Solution Architecture; implementation and qualification by Engineering/QA.  
**Related requirements:** FR-MOB-014–016, FR-MOB-021, FR-MOB-025, FR-MOB-012.  
**Related documents:** BRD 1.4; SAD 1.13 Section 8.4.

#### Context

Apple execution needs prepared toolchains, devices, signing and WDA, but customers/providers own provisioning. Remote JVM workers need no local Xcode.

#### Decision

Use the existing environment/provider boundary to consume configured Appium endpoints and authorized targets. Support customer-managed local/remote and generic compatible cloud endpoints, authentication secret references and namespaced typed capabilities. Preserve exact endpoint paths and server-side app reference semantics. Preflight validates configuration and observable prerequisites, reports remote unknowns honestly and uses initialization for final session confirmation. Signing, device preparation, WDA and services remain infrastructure-owned.

#### Consequences

Setup guidance and sanitized diagnostics are essential. Endpoint status alone cannot prove target readiness. No paid provider, hidden device provisioning or vendor certification is required.

#### Alternatives Considered

Embed Xcode/Appium/device provisioning in runtime or MCP image: incompatible with ownership and remote topology. Treat status reachability as full readiness: conceals missing WDA/device/signing prerequisites.

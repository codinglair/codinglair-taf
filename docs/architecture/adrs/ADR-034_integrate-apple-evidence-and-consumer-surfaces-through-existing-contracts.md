# ADR-034: Integrate Apple Evidence and Consumer Surfaces Through Existing Contracts

**Status:** Proposed architecture decision for TAF 1.3.0; approval not asserted.  
**Date:** September 30, 2026  
**Decision owner role:** Solution Architecture; implementation and qualification by Engineering/QA.  
**Related requirements:** FR-MOB-019, FR-MOB-023–026.  
**Related documents:** BRD 1.4; SAD 1.13 Section 8.4.

#### Context

Controller support alone does not satisfy the Apple increment; discovery, generation and usable released consumers are required.

#### Decision

Reuse ArtifactCollector, redaction/reporting, the shared capability manifest, readiness resources and authorized coarse-grained MCP jobs. Represent conditional artifact availability; do not require video to execute. Extend the existing mobile starter/contribution with Apple platform/mode/target selection and runnable external native/hybrid/Safari examples. Preserve framework runner lifecycle and Android defaults. The updated MCP image is a remote Apple client and supports existing STDIO/Streamable HTTP profiles.

#### Consequences

No second dependency graph, copied runner or raw-driver MCP tool surface. Secret-bearing capabilities, errors and artifact links require sanitization. Generated projects compile before infrastructure is supplied; execution uses consolidated preflight. Public architecture/consumer docs must be updated.

#### Alternatives Considered

Ship only IOSDriver integration: omits required consumer/MCP/evidence scope. Per-XCUITest-command MCP tools: expands unneeded security and maintenance surface. Mandatory cloud/video: contradicts BRD.

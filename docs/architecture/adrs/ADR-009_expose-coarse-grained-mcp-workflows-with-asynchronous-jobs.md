# ADR-009: Expose coarse-grained MCP workflows with asynchronous jobs

**Status:** Accepted  
**Date:** 2026-07-24  
**Decision owners:** Product Owner and Solution Architecture  
**Scope:** Codinglair TAF product family

## Context

Raw controller operations such as click, SQL, and message consumption would make agents slow, brittle, expensive, and overly powerful. Compilation and test execution are long-running operations that do not fit one synchronous request.

## Decision

- Expose MCP resources, prompts, and coarse-grained tools such as validate, scaffold, compile, execute, inspect, retrieve, cancel, and prepare defect candidate.
- Do not expose unrestricted low-level controller, shell, SQL, browser, or filesystem operations by default.
- Support STDIO for local clients and Streamable HTTP for remote clients.
- Represent long-running work as persistent jobs with status, progress, cancellation, result references, and safe checkpoints.
- Restart test execution from a safe boundary rather than claiming mid-instruction resume.
- Apply the same schemas and safety constraints to native and external agents.

## Consequences

- Agents interact through stable workflows instead of vendor details.
- Long tasks remain observable and cancellable.
- Advanced diagnostics may require separately privileged tools.
- Tool and job schemas require versioning and compatibility tests.

## Alternatives considered

- Expose every controller action — rejected for security and brittleness.
- Synchronous-only MCP — rejected because test jobs exceed normal interaction time.

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

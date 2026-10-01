# ADR-009: Expose Coarse-Grained MCP Workflows with Asynchronous Jobs

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

**Context** Raw controller operations such as click, SQL, and message consumption would make agents slow, brittle, expensive, and overly powerful. Compilation and test execution are long-running operations that do not fit one synchronous request.

**Decision**

- Expose MCP resources, prompts, and coarse-grained tools such as validate, scaffold, compile, execute, inspect, retrieve, cancel, and prepare defect candidate.
- Do not expose unrestricted low-level controller, shell, SQL, browser, or filesystem operations by default.
- Support STDIO for local clients and Streamable HTTP for remote clients.
- Represent long-running work as persistent jobs with status, progress, cancellation, result references, and safe checkpoints.
- Restart test execution from a safe boundary rather than claiming mid-instruction resume.
- Apply the same schemas and safety constraints to native and external agents.

**Consequences**

- Agents interact through stable workflows instead of vendor details.
- Long tasks remain observable and cancellable.
- Advanced diagnostics may require separately privileged tools.
- Tool and job schemas require versioning and compatibility tests.

# MCP-006 coarse-grained workflow tools

`taf-mcp-tools` is the transport-neutral application boundary for validation, compilation, build,
selected test execution, and cancellation. It depends on the existing job, security, and isolated
worker modules; Runtime has no dependency on MCP.

Requests contain structured project, environment, workspace, selector, timeout, idempotency, and
approval fields. The service normalizes and confines workspaces to an administrator-approved root,
accepts only bounded selectors, and invokes MCP-003 enforcement before validation or job side
effects. It never accepts an executable, shell text, or arbitrary arguments.

Compile, build, and execute return a `taf://job/{id}` reference immediately and run through an
injected executor service whose ownership transfers to `McpWorkflowTools`; closing the service
closes that executor. Rejected scheduling cancels the newly created durable job rather than leaving
orphaned queued work. The durable job records sanitized project/environment scope and a bounded
`workflow.outcome` event so compile, test, environment, cancellation, and timeout results survive
restart. Successful large results remain controlled `taf://` references.

All request strings are checked against the configured secret-canary redactor before job creation.
Sensitive input is rejected, enforcement/audit input is sanitized, and persisted job payloads are
sanitized again as defense in depth. Authorization denial and missing approval have distinct
client outcomes and never create a job.

`WorkflowNameResolver` is deployment-owned. It maps the already validated operation,
environment, and optional selector to a preconfigured worker workflow name. The local worker still
executes only its administrator-defined fixed argv allowlist; request data is never interpolated
into a command line. A deployment must fail closed when no selector mapping exists.

Transport adapters remain MCP-008/MCP-009 scope. Report/evidence content retrieval remains MCP-010
scope; MCP-006 returns metadata and controlled references only.

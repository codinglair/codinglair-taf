# MCP-009 Streamable HTTP and OIDC boundary

`taf-mcp-transport-http` is the remote Spring MVC adapter for the transport-neutral MCP-003,
MCP-005, and MCP-006 services. Spring AI 2.0.1 owns the `/mcp` Streamable HTTP protocol endpoint;
the adapter does not duplicate protocol framing or workflow behavior.

## Authentication and authorization

- Every `/mcp` request requires an OAuth 2.0 bearer JWT from `taf.mcp.http.issuer-uri`.
- Standard timestamp validation and the configured `taf.mcp.http.audience` are mandatory.
- Subject, scopes, project, environment, and optional agent identity come only from verified JWT
  claims. Remote payloads cannot override authorization context.
- Tools require `taf.tools.<operation>`; resources require `taf.resources.read`. The resulting
  identity then passes through the existing most-restrictive MCP-003 policy boundary.
- A missing agent claim represents a human identity; a present agent claim represents an agent.
  Worker and workload credentials remain separate execution-boundary identities.

## Bounds and operations

The servlet admission filter rejects oversized declared bodies, excess concurrent requests,
excess distinct sessions, and per-origin rate exhaustion before MCP parsing. Workflow timeouts are
capped by the configured server request timeout. Defaults are 1 MiB, 64 concurrent requests, 256
sessions, 120 requests/minute, and 30 seconds.

Only Actuator health, liveness, and readiness are externally permitted. Other actuator and
application paths are denied by the transport security chain. Configuration contains issuer and
claim names only—never client secrets or resolved credentials.

STDIO and HTTP use matching v1 request/result contracts. HTTP intentionally removes the STDIO
project/environment request fields because remote authorization context is server-owned.

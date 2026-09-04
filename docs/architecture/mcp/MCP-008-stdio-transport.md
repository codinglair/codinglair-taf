# MCP-008 STDIO transport

The `taf-mcp-transport-stdio` module is the local process binding for the transport-neutral
MCP-005 resources and MCP-006 workflow services. It uses Spring AI MCP 2.0.1 and JSON-RPC over
newline-delimited standard input/output. Protocol output is reserved exclusively for stdout;
Spring Boot and application logging are explicitly directed to stderr.

The transport supplies a deployment-configured local identity and always marks enforcement
requests as `STDIO`. Callers cannot provide or override identity, roles, or transport. Tool input
uses the versioned MCP-006 request envelope, and all tool/resource calls delegate to the shared
authorization, approval, audit, redaction, job, and workflow services.

Before Spring AI reads stdin, `StdioProtocolInput` enforces a one-MiB newline-frame limit and
discards malformed JSON frames with sanitized stderr diagnostics. A bad frame therefore does not
terminate the protocol reader or contaminate stdout.

On application shutdown, `StdioShutdownCoordinator` requests cancellation for recoverable jobs,
closes the shared workflow executor, and durably completes remaining cancellation requests. This
preserves the MCP-002 lifecycle and prevents process-owned workers from surviving the server.

The external smoke suite launches a real Java process through Spring AI's STDIO client transport on
Windows, verifies discovery and resource retrieval, and exercises validate, execute, and cancel.
It also verifies recovery after a malformed raw frame. Unit tests cover shutdown cleanup and the
default-deny authorization boundary.

## Dependency isolation

- Spring AI BOM/server/client 2.0.1 (Apache-2.0) provides the standards protocol implementation.
- The module pins Jackson annotations 2.21 (Apache-2.0) locally because Spring AI's Jackson 3
  mapper requires `JsonSerializeAs`, absent from the repository-wide Jackson 2.19.2 annotation jar.
- The module pins json-schema-validator 3.0.0 (Apache-2.0) locally because the MCP SDK requires its
  3.x dialect API, absent from the repository's 1.5.9 version.

The two compatibility overrides are direct dependencies of this transport only; they do not
change dependency resolution for Runtime or other MCP modules.

# MCP-007 governed scaffold workflow

`taf-mcp-tools` owns a transport-neutral scaffold boundary for proposed working-tree changes. Server
composition registers immutable templates by exact name and version. Requests can select a template
and a destination beneath an approved workspace; they cannot submit template content, commands, or
Runtime-controller implementations.

The initial workflow is deliberately create-only. Every target must be absent and confined beneath
the approved destination. Existing files, symbolic workspace roots, traversal paths, and concurrent
create conflicts fail closed. This preserves unrelated dirty-tree content without attempting an
unsafe textual merge.

All writes pass the shared MCP-003 `scaffold` enforcement boundary and require its digest-bound
working-tree approval. A template containing any `DEPENDENCY` asset additionally requires a
separate approval bound to the `dependency.add` action, `taf:dependency:write` permission, and the
same canonical template/destination digest. Neither approval substitutes for the other.

Successful writes return a bounded create summary, exact template identity, operation timestamp,
relative created-file paths, asset kinds, and SHA-256 content digests. Template content is not echoed
in the diff or provenance. Compilation is delegated to an internal server-owned `ScaffoldCompiler`;
request content never becomes argv. Compiler detail is intentionally reduced to a generic
success/failure summary.

## Composition and configuration

`ScaffoldWorkflow` is intentionally Spring-free. Application composition constructs it with the
approved workspace root, template registry, MCP-003 enforcement and approval services, a
server-owned `ScaffoldCompiler`, and a clock. There is no component scan, implicit compiler bean,
or MCP-007 `@ConfigurationProperties` contract.

The compiler implementation must delegate to a fixed administrator-configured workflow such as the
MCP-004/MCP-006 worker boundary; it must not accept request-controlled executables, arguments, or
flags. Timeout, process, output, and resource limits belong to that worker/workflow configuration,
not to scaffold requests. STDIO and Streamable HTTP application wiring arrive in MCP-008 and
MCP-009 respectively. Adding Spring auto-configuration or a public compiler customization SPI would
require separately approved scope and compatibility documentation.

Detailed sanitized compilation diagnostics remain MCP-010. MCP-007 returns only the generic
`scaffold compiled` or `scaffold compilation failed` outcome.

Rollback passes through the same digest-bound MCP-003 write approval, accepts operation provenance,
and removes a file only when it remains a regular non-link
file beneath the workspace and its current digest equals the created digest. Any user edit causes
the complete rollback to be refused before deletion. Provenance template identity, paths, and asset
kinds must exactly match the registered template. Dependency-bearing rollback revalidates both the
write and dependency approvals before deletion. The workflow never commits, pushes, tags,
creates a pull request, publishes, or changes Runtime contracts.

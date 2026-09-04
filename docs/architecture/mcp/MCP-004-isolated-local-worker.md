# MCP-004 isolated local execution worker

## Boundary and protocol

`taf-execution-worker` is a Spring-free, extractable worker module. Protocol version `1.0` accepts a
`WorkerRequest` containing an opaque job ID, an administrator-defined workflow name, a canonical
source workspace, a bounded timeout, controlled relative artifact paths, and a correlation ID. It
does not accept a shell command or request-supplied arguments. A `WorkerResult` returns only a
bounded sanitized summary, terminal worker status, duration, and an artifact manifest containing
size, SHA-256, and controlled `taf://artifact` references.

## Execution isolation

- `WorkerCommand` is startup configuration with fixed argv and an absolute, regular, non-link
  executable. `ProcessBuilder` is invoked directly; no shell parses request content.
- The source checkout is never the process working directory. Before launch the worker rejects
  symbolic links and special files, applies file/byte quotas, and copies the declared tree into a
  unique disposable directory under the configured execution root.
- All client-selected paths are relative, normalized, and required to remain under that copied
  workspace. The worker deletes the copy after success, failure, cancellation, timeout, and setup
  failure. Consequently approved worker writes cannot alter any source path, including dirty files.
- The worker inherits the configured operating-system identity. Deployment is responsible for
  assigning that process identity least privilege and for applying platform CPU/memory limits;
  the module enforces time, workspace, output, artifact-count, and artifact-byte limits. This local
  provider does not claim an OS security boundary against a malicious build descriptor; production
  deployment must combine it with the ADR-012 non-root/container sandbox profile.

## Lifecycle, cancellation, and results

Stdout and stderr are merged, drained concurrently on a virtual thread, byte bounded, decoded as
UTF-8, and passed through the MCP-003 redactor. Timeout, cancellation, interruption, and unexpected
failure terminate descendants before the root, escalate to forced termination, and then clean the
workspace. `JobExecutionCoordinator` moves a queued job to `RUNNING`, polls durable
`CANCEL_REQUESTED`, acknowledges `CANCELLED`, attaches references only on success, and maps timeout
or non-zero completion to `FAILED`. A pre-launch cancellation never starts a process.

Artifacts must be explicitly listed regular files inside the copied workspace. Total count and
bytes are bounded; a secret-canary match rejects publication. `LocalArtifactStore` copies accepted
files to a separately configured controlled root. Artifact content is never placed in a job or MCP
response.

## Compatibility and deferred composition

This is an additive Java contract and changes neither Runtime nor MCP v1 schemas. MCP-006 will own
tool-to-workflow mapping and canonical request construction. MCP-009 will own transport and workload
authentication. Container CPU/memory/network enforcement and dedicated non-root identity are
deployment controls, not portable JDK APIs, and remain mandatory for the production worker profile.

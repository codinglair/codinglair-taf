# MCP job persistence and recovery

`taf-mcp-jobs` defines the location-independent durable job boundary. `JobRepository` stores immutable, optimistically versioned `Job` snapshots; the initial `LocalJobRepository` uses one atomic file per job with per-job process and operating-system locks. Its binary format begins with a magic value and format version `1`. Unsupported or corrupt data fails closed with a payload-free `JobPersistenceException`.

The state machine permits queued work to run or cancel, running work to succeed, fail, request cancellation, or become recovery-pending, and recovery-pending work to return to the queue or cancel. Successful, failed, and cancelled jobs are immutable. Cancellation converges on `CANCEL_REQUESTED` and is idempotent. A worker remains responsible for acknowledging that request by transitioning to `CANCELLED` after descendant cleanup.

After control-plane restart, a previously running job becomes `RECOVERY_PENDING` while retaining its last explicitly recorded safe checkpoint. A later worker may requeue it and restart from that workflow boundary. Neither the model nor persistence layer represents an instruction pointer, so the system never claims mid-instruction resume.

Payload keys/values, progress events, checkpoints, and reference counts are bounded by `JobLimits`. Events are append-only and their sequence is contiguous. Large results are never embedded: completed jobs contain only controlled `taf://` `JobReference` values. Callers must pass already-sanitized payloads; value-independent redaction and secret-canary enforcement belong to the shared MCP-003 enforcement boundary.

The local provider is suitable for the initial modular-monolith deployment. Alternate database providers implement `JobRepository` without changing the domain or worker-facing contracts. The persistence root rejects symbolic links, filenames encode opaque MCP identifiers portably, writes use atomic replacement where supported, and optimistic version conflicts are explicit rather than silently overwriting state.

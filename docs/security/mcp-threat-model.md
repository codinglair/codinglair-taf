# TAF MCP threat model v1

## Scope and trust boundaries

The MCP client/model, control plane, persistence, isolated execution worker, checked-out workspace, secret provider, systems under test, and artifact store are separate trust zones. Model output, repository content, test data, browser content, build output, resource content, and prompt arguments are untrusted data. The control plane authenticates and authorizes; workers alone resolve secret references and execute allowlisted build/test workflows.

## Threats and required controls

| Threat | Boundary | Required control and verification |
|---|---|---|
| Prompt or repository content attempts to direct the server | client/model to control plane | Treat content as data; schema validation, coarse-grained allowlist, no dynamic tool registration |
| Unrestricted shell, SQL, browser, or filesystem access | control plane to worker/SUT | No such tool schemas; map requests to deterministic workflows and approved paths only |
| Path traversal or overwrite of unrelated changes | control plane to workspace | Canonicalize within the approved workspace, deny links/escape, proposed diff only, separate dependency approval |
| Resolved secret reaches MCP/model/log/artifact | secret provider to worker/control plane | References only in contracts; reject conventional secret-bearing argument names as defense in depth; worker-local resolution; MCP-003 canary leak tests and redaction regardless of property name before every return/persist boundary |
| Caller accesses another project/environment/job | identity to control plane/storage | OIDC identity plus role, user, project, environment, action, and agent authorization; bind references/cursors to the decision |
| Consequential action bypasses approval | transport to service | Default deny; enforce approval below both transports; bind approval to principal, digest, scope, action, and expiry |
| Replay or duplicate mutation | client to tool/job | Required idempotency key for mutations/jobs; caller-scoped deduplication; cancellation targets an idempotent terminal state |
| Resource exhaustion or unbounded data exfiltration | client/worker/artifact store | Schema size limits, pagination, quotas, timeouts, worker CPU/memory/process limits, bounded summaries and controlled references |
| Worker escape or descendant survives cancellation | worker to host | Separate process identity, constrained workspace/network/commands, descendant termination and cleanup verification |
| Audit tampering or sensitive audit payload | control plane to audit store | Append-only contract, correlation and decision metadata, integrity controls, never record values or raw sensitive payloads |
| Confused deputy across STDIO and HTTP | transport to policy enforcement | One transport-neutral contract and service authorization boundary; protocol adapters cannot assert permissions |
| Stale checkpoint resumes unsafe instruction | persistence to worker | Resume only from a validated safe workflow boundary; never claim mid-instruction continuation |

Residual risk remains until MCP-002–010 implement persistent jobs, authorization/approval/audit, worker isolation, tools, resources, prompts, transports, and client compatibility tests. MCP-001 defines their enforceable contract baseline; it does not claim those controls are operational.

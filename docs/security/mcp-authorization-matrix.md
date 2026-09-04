# MCP authorization and operation matrix v1

The most restrictive applicable user, role, project, environment, action, and agent policy wins. Missing identity, permission, project/environment binding, required approval, or valid target reference is denied. Human, agent, workload, worker, and test-persona identities are never interchangeable.

| Tool | Permission | Approval | Audit | Timeout | Idempotency | Side effect / boundary |
|---|---|---|---|---|---|---|
| discover | `taf:capability:read` | never | invocation | 10s / 30s max | safe | none |
| validate | `taf:project:read` | never | invocation and result | 30s / 120s | safe | read-only synchronous validation |
| scaffold | `taf:working-tree:write` | required | invocation and result | 60s / 300s | key required | approved-path proposed working-tree diff |
| compile | `taf:worker:execute` | policy | full lifecycle | 600s / 3600s | key required | isolated asynchronous worker |
| build | `taf:worker:execute` | policy | full lifecycle | 900s / 7200s | key required | isolated asynchronous worker |
| execute | `taf:test:execute` | policy | full lifecycle | 1800s / 86400s | key required | isolated asynchronous worker/SUT access |
| inspect | `taf:job:read` | never | invocation | 10s / 30s | safe | authorized job metadata only |
| retrieve | `taf:artifact:read` | never | invocation and result | 30s / 120s | safe | bounded authorized resource |
| cancel | `taf:job:cancel` | policy | full lifecycle | 30s / 120s | target-state idempotent | job state and worker descendants |
| report | `taf:report:read` | never | invocation and result | 30s / 120s | safe | bounded sanitized report |

Resource reads additionally require ownership/project/environment checks. Evidence and reports are returned only through expiring controlled references or bounded redacted content. Prompts inherit the permissions of every resource they reference and cannot invoke tools or elevate approval themselves.

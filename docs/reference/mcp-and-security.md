# MCP, schema, security, approval, and audit reference

## Versioned contracts

The `v1` schema set is authoritative: [common](../../taf-mcp-server/taf-mcp-contracts/src/main/resources/META-INF/taf/mcp/schema/v1/common.schema.json),
[tool request](../../taf-mcp-server/taf-mcp-contracts/src/main/resources/META-INF/taf/mcp/schema/v1/tool-request.schema.json),
[tool response](../../taf-mcp-server/taf-mcp-contracts/src/main/resources/META-INF/taf/mcp/schema/v1/tool-response.schema.json),
[resource](../../taf-mcp-server/taf-mcp-contracts/src/main/resources/META-INF/taf/mcp/schema/v1/resource.schema.json),
[prompt](../../taf-mcp-server/taf-mcp-contracts/src/main/resources/META-INF/taf/mcp/schema/v1/prompt.schema.json), and
[capability catalog](../../taf-mcp-server/taf-mcp-contracts/src/main/resources/META-INF/taf/mcp/schema/v1/capability-catalog.schema.json).
STDIO and Streamable HTTP use schema-equivalent payloads.

## Tools, resources, and prompts

Tools expose bounded `discover`, `validate`, `scaffold`, `compile`, `build`, `execute`, `inspect`,
`retrieve`, `cancel`, `report`, and `diagnose` workflows. Long operations return persistent job IDs.
Resources provide controlled capability, job, result, evidence, and diagnostic views. Prompts are
curated templates; their content is untrusted input and cannot expand permissions. No surface
provides unrestricted shell, SQL, browser, or filesystem access.

### EventBridge and SQS jobs

The v1 request remains backward compatible and additively accepts `requiredCapabilities`. Each
entry identifies the stable capability (`aws.eventbridge` or `aws.sqs`), a configured logical
instance, and the exact operations needed by the coarse-grained job. Before dispatch, the server
fails closed unless the capability is installed, the instance exists and is ready in the caller's
authorized environment, the requested operations are permitted, and ownership/isolation settings
are compatible. Request timeouts and evidence limits remain bounded by the existing job policy.

Capability discovery reports versions, supported operations, limitations, and required
configuration. Instance discovery reports only logical names, safe resource aliases,
ownership/isolation modes, readiness states, and controlled diagnostic codes. It never returns
credentials, resolved secret-bearing endpoints, receipt handles, authorization headers, AWS SDK
objects, or unsanitized message/event payloads. EventBridge routing remains a coarse-grained
workflow verified through its configured SQS target; MCP does not expose queue, rule, bus, or
permission mutation tools.

```json
{
  "arguments": {"requiredCapabilities": [
    {"capabilityId":"aws.eventbridge","instance":"orders","operations":["publish","verify-route"]},
    {"capabilityId":"aws.sqs","instance":"orders-target","operations":["await"]}
  ]}
}
```

## Authorization and approvals

HTTP authenticates with OIDC/OAuth 2.0 and validates issuer, audience, expiry, and scope. Effective
authorization is the most restrictive applicable user, role, project, environment, action, and
agent policy. Consequential actions are default-deny and require an approval reference where the
tool contract declares one. STDIO is not an enforcement bypass.

Audit records cover authentication, authorization, approval, tool call, job transition,
cancellation, environment access, secret-access metadata, and external publication. Records never
contain resolved secret values. Redact before logging, persistence, transport, reporting, or error
construction. Jobs have bounded output, immutable terminal states, idempotent cancellation, and
worker cleanup that terminates descendants.

## Persistent job model

`QUEUED` work can start, accept a cancellation request, or be cancelled before execution.
`RUNNING` work reports monotonic progress and may record a safe checkpoint. It may request
cancellation, become `RECOVERY_PENDING` after restart, or finish as `SUCCEEDED`/`FAILED`.
`RECOVERY_PENDING` returns to `QUEUED` only at a safe restart boundary, or proceeds toward
cancellation. `SUCCEEDED`, `FAILED`, and `CANCELLED` are immutable terminal states.

Cancellation requests are idempotent. Events are append-only, immutable identity/payload fields
cannot change, and result references attach only on success. Restart never claims mid-instruction
resume. Clients should poll/inspect by job ID, treat progress as advisory, retrieve large results by
controlled reference, and stop retrying a terminal job.

```text
QUEUED -> RUNNING -> SUCCEEDED | FAILED
   |         |  \
   |         |   -> RECOVERY_PENDING -> QUEUED
   |         -> CANCEL_REQUESTED -> CANCELLED | FAILED
   -> CANCEL_REQUESTED | CANCELLED
```

## Secret and redaction examples

Safe request/configuration data contains a reference, never a value:

```yaml
credential-reference: secret://env/ORDERS_API_TOKEN
```

Unsafe fields such as `password`, `token`, `privateKey`, or credential-bearing connection strings
are rejected from generic MCP arguments. Sanitized diagnostics may report
`credential-reference=secret://env/ORDERS_API_TOKEN, outcome=FAILED`; they must never include what
the resolver returned. Redaction occurs before logs, audit, job events, MCP responses, reports, and
artifact persistence. Error handling must sanitize both messages and causes, and tests for a new
path must inject a canary value and assert it is absent from every output.

The detailed controls are the [authorization matrix](../security/mcp-authorization-matrix.md) and
[threat model](../security/mcp-threat-model.md).

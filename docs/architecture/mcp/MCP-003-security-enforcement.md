# MCP-003 shared security enforcement

`taf-mcp-security` is the transport-neutral control-plane boundary for MCP authorization,
human approval, audit, and response redaction. STDIO, Streamable HTTP, and internal adapters must
delegate an operation to `McpEnforcementService`; they must not call the operation first or
reimplement a subset of these checks.

Before authorization or execution, the shared service validates the canonical request input with
`InputSizeValidator`. The default boundary allows at most 1 MiB of UTF-8 scalar/key content, 32
levels of nesting, and 10,000 aggregate map/list/array elements. Deployments may supply stricter
positive `InputLimits`. Every transport receives the same `INPUT_REJECTED` result, the input is not
copied into audit, and the operation callback is never invoked. Transport-native frame/body limits
remain an additional outer control, not a replacement for this boundary.

## Authorization and approval

`AuthorizationContext` binds the user, roles, agent identity, project, environment, action, and
permission. `AuthorizationPolicyEngine` selects every applicable rule. No applicable allow rule is
a denial, and any applicable deny rule overrides every allow rule. Matching approval requirements
are combined restrictively. `scaffold` is always consequential and approval-required; deployments
may configure additional consequential actions such as external publication.

An `ApprovalRequest` is bound by SHA-256 to the complete authorization context and an
adapter-supplied canonical operation digest. Only a separate human identity can decide it. A
decision is terminal, expiry is checked during enforcement, and an approval cannot be replayed for
a different user, agent, project, environment, action, permission, or operation.

## Audit and redaction

`AuditLog` is the location-independent append-only SPI. `InMemoryAuditLog` is the initial
single-process provider and assigns contiguous sequence numbers under a monitor. Its returned
events and detail maps are immutable snapshots. Production persistence, integrity hashing, policy
retention, and multi-node ordering remain provider/operations follow-up scope.

`ResponseRedactor` recursively sanitizes maps, iterables, arrays, and strings. Sensitive property
names have their values replaced, while configured secret canaries are removed independent of
property name. The in-memory audit provider applies the same redactor at append time, providing a
second boundary even when a caller supplies unsanitized audit metadata. Credential resolution is
audited by provider and opaque reference only; resolved values have no method parameter or event
field.

The module is JDK-only in production and has no dependency on Runtime, Spring, a transport, or a
secret provider. OIDC authentication and transport integration are MCP-009 scope; worker-local
secret resolution is MCP-004 scope.

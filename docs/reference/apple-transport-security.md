# Apple transport and artifact security

SEC-130-001 adds resource checks under the Product Owner's 2026-10-02 disposition.
Action authorization and local SecretManager remain authoritative. Secret retrieval
does not establish permission to access a resource.

## Trusted standalone configuration

Existing Apple constructors and the Spring starter snapshot exact resources from
explicit operator configuration: Appium/WDA endpoints, target/selection/allocation,
application reference and bundle ID. No MCP or external service is required.
Never construct a trusted policy from job overrides: capture the trusted baseline
first and check the resolved settings against it. Endpoint queries/userinfo/
fragments and ambiguous traversal paths are rejected; custom paths are preserved.

`AppleTransportSecurity.target(settings)` identifies the declared allocation,
explicit device ID, deterministic provider-selection hash or configured device
name. Provider selection needs no enumeration of every device. An authorized
allocation permits its provider-resolved target, not a job-selected alternative.
Global allocation/exclusivity remains provider-owned.

Supply the existing SecretManager bean for credential-bearing execution. Without
it execution fails closed; startup/discovery never resolves secrets. Credential
profile aliases still require an actual provider; none is fabricated here.

```yaml
taf:
  mobile:
    apple:
      authentication:
        mechanism: HEADER
        secret-references:
          Authorization: secret://env/APPLE_AUTHORIZATION_HEADER
      provider-options:
        "[cloud:options]":
          account:
            accessKey:
              secretReference: secret://env/APPLE_ACCESS_KEY
```

HEADER references hold complete header values, including an approved Bearer prefix.
Host/framing/connection header overrides are forbidden. BASIC requires exactly
username/password references. PROVIDER_CAPABILITY maps paths such as
`/cloud:options/account/accessKey` to references. Paths cannot overlap or replace
typed/resource fields or existing ownership. Nested reference objects contain
exactly one secretReference field. Plaintext sensitive provider/descriptor fields
fail closed. Resolution happens inside each authorized exchange; holders close
immediately. Temporary String/wire copies are scoped to that exchange and never
persisted. Options and retained driver capabilities contain no resolved values.
Both W3C alwaysMatch and firstMatch shapes are supported.

## Governed execution

Runtime Core owns ResourceAccess/ResourceAuthorizer, without MCP dependencies.
Kinds are ENDPOINT, TARGET, APPLICATION, ARTIFACT_DESTINATION and
CREDENTIAL_FORWARDING. Grants match an exact resource and action, or `*` actions
on that exact resource; no implicit resource wildcard exists. `trusted` snapshots
grants; `intersect` applies additional restrictions. Diagnostic strings omit IDs.

MCP ResourcePolicyRule supplements existing PolicyRule caller selectors. The
additive three-argument AuthorizationPolicyEngine constructor accepts resource
rules. `decideResource` requires action and resource grants; applicable denies
win. Legacy action-only rules grant no resources implicitly.
`McpEnforcementService.resourceAuthorizer(enforcementRequest)` binds the caller,
project/environment/action/permission and approved operation digest, rechecks
approvals, and audits kind/action/decision/rule IDs without raw resource IDs.

Pass that adapter, SecretManager and the actual environment into
AppleTransportSecurity at worker transport construction. The additive five-argument
Apple controller constructor accepts it. Spring also supports an explicit
AppleTransportSecurity or ResourceAuthorizer bean. A ResourceAuthorizer bean
restricts Android construction/exchanges; absent it trusted Android behavior is
unchanged. For standalone restrictions supply an intersection with trusted grants.

The worker's additive constructor accepts administrator-owned workflowResources.
`execute(request, cancellation, authorizer)` checks them before workspace copying
or process launch. The original overload denies protected workflows without a
policy. Workflows without declarations retain their behavior. Child Runtime
services must use the caller-bound policy for dynamic requests too: declarations
are not a network sandbox or a substitute for transport enforcement. Never infer
grants from untrusted job input. New Apple job/catalog wiring remains MCP-130-001.

Apple checks resolved resources before reservation/connection and on exchanges.
Application commands and native application calls check actual app/bundle IDs
and wire command actions; normal configured application access uses initialize.
Denials precede credential retrieval. Credential failure before connection
releases reservations; attempted ambiguous creation retains ownership quarantine.
Cleanup uses the same authenticated authorized transport and only owned IDs.

## Artifact destinations and output

ArtifactDownloadTransport authorizes the initial URL and every redirect before
connecting. Identifiers are normalized scheme/host/effective-port plus exact raw
path. Signed query tokens remain on the wire, not in grants or diagnostics.
Unsupported schemes, userinfo, fragments, traversal and encoded path ambiguity
fail closed; ordinary encoded path characters are permitted. Explicit limits
permit at most five redirects, 64 MiB and ten minutes. Reads remain bounded after
headers arrive. TLS uses normal JDK verification; HTTPS downgrade is denied.

Credential headers remain on their original origin. Cross-origin forwarding
requires a separate CREDENTIAL_FORWARDING grant identified by
`origin(initial) + " -> " + origin(destination)` with action forward. Destination
trust alone never authorizes forwarding. Failures omit raw URLs, headers, tokens
and bodies. Returned bytes still require existing evidence filtering before
ArtifactCollector retention; URL access is not permission to publish content.

Appium redirects are forbidden. Responses are bounded to 4 MiB and scrubbed
before Selenium sees them. Error bodies/stacks become neutral protocol errors.
Registered credential values, nested sensitive keys, URI userinfo and signed
queries are removed. Core reporting and MCP scrub URL/JSON patterns too.
Screenshot/video/source responses are suppressed when visual authorization is
absent or credentials prevent reliable content sanitization. Acquisition remains
MOB-130-005; this change claims no device-log/video availability and performs no
automatic provider artifact download. WebSockets/native HTTP bypasses are denied.
Disable `jdk.httpclient.HttpClient.log`: protected transports fail closed when
JDK wire diagnostics would bypass redaction. Selenium debug logs see only
reference-bearing requests and sanitized responses.

## Compatibility and evidence

No existing controller, descriptor, runner, factory or worker signature was removed.
Android defaults/trusted behavior remain intact. Apple descriptor validation is
intentionally stricter for plaintext credentials. Internal dependencies use the
existing reactor version; no external dependency/provider was added.
Security fixtures cover HTTP protocols, authentication, nested references,
denials/overrides, redirects/forwarding, body bounds, cleanup, interruption,
Spring composition and retained-output canaries. They do not qualify live devices.

# MCP image and runtime profile contract

This is the version 1 contract for the official `codinglair/codinglair-taf-mcp` image. It defines
packaging and operator-facing behavior; MCP-120-002 implements the launchers and INF-120-001 builds
and publishes the image. The packaged JSON schemas are authoritative for serialized values.

## Image and compatibility

Immutable semantic tags such as `codinglair/codinglair-taf-mcp:1.2.0` are the supported release
identity. `latest` may point to the same digest at publication time, but is unsupported in CI and
Kubernetes manifests. Consumers must compare the image's OCI version and digest with packaged
`META-INF/taf/mcp/catalog/v1/image-compatibility.json`. Compatibility is an exact TAF release,
MCP schema major, and Java runtime tuple; a tag alone is not compatibility evidence.

The single image contains the Java 25 runtime; MCP contracts, jobs, security, resources, tools,
prompts, and both transport adapters; required TAF Runtime libraries; the isolated worker launcher;
and license, SBOM, provenance, and compatibility metadata. It contains no credentials, deployment
configuration, consumer project, generated workspace, job/audit database, or retained result.

The sole entrypoint is `/opt/taf/bin/taf-mcp` and requires exactly one first argument:
`stdio` or `streamable-http`. Missing, repeated, combined, or unknown profiles fail before server
initialization with a non-zero exit code and a sanitized stderr message. `docker attach`,
`kubectl attach`, and `kubectl exec` are administrative mechanisms, never MCP transports.

## Configuration contract

Configuration precedence, highest first, is an entrypoint option, environment variable, mounted
configuration file `/etc/taf-mcp/application.yaml`, then the safe packaged default. Unknown options
and invalid values fail closed. Command-line options and environment variables may contain
non-sensitive settings and secret-reference aliases, never resolved secret values.

| Setting | Entry point / environment | Default | Contract |
| --- | --- | --- | --- |
| Profile | first argument / `TAF_MCP_PROFILE` | none | Exactly one profile; argument and environment must agree if both exist |
| MCP port | `--port` / `TAF_MCP_PORT` | `8080` | Streamable HTTP only, TCP 1–65535 |
| Workspace | `--workspace` / `TAF_MCP_WORKSPACE` | `/workspace` | Canonical mounted path; no host-path disclosure or escape |
| State directory | `--state-dir` / `TAF_MCP_STATE_DIR` | `/var/lib/taf-mcp` | HTTP durable-state adapters only; explicit writable volume |
| Secret provider | `--secret-provider-ref` / `TAF_MCP_SECRET_PROVIDER_REF` | none | Opaque provider/profile alias, never credential material |
| OIDC issuer/audience | config or `TAF_MCP_OIDC_ISSUER`, `TAF_MCP_OIDC_AUDIENCE` | none | Required for remote HTTP; HTTPS issuer and exact audience |
| Limits | config or `TAF_MCP_RATE_LIMIT`, `TAF_MCP_PARALLEL_LIMIT` | fail if absent remotely | Positive bounded values, enforced per identity/project/environment |
| Shutdown | config or `TAF_MCP_SHUTDOWN_TIMEOUT` | `30s` | Positive bounded drain deadline |

The image declares `/workspace`, `/var/lib/taf-mcp`, `/tmp`, and `/etc/taf-mcp` as its only runtime
mount locations. `/etc/taf-mcp` is read-only. `/workspace` is mounted only when an approved workflow
needs it and is scoped to that workspace. `/tmp` is ephemeral. STDIO does not require durable state;
HTTP job state, approvals, audit history, and retained results are externalized before replicas
exceed one. Image filesystems are read-only and the process runs non-root.

## STDIO profile

The supported invocation shape is:

```text
docker run --rm -i --read-only --mount type=bind,src=<approved-workspace>,dst=/workspace codinglair/codinglair-taf-mcp:1.2.0 stdio
```

The client owns process creation, stdin closure, cancellation, and removal. No pseudo-TTY (`-t`),
detached mode, published MCP port, attach, or exec bridge is supported. Stdout contains MCP protocol
frames only; banners, diagnostics, and logs go to stderr. Health is a successful MCP initialize
exchange followed by ping. EOF requests graceful shutdown; fatal framing/configuration errors exit
non-zero. Process lifetime is the session lifetime.

## Streamable HTTP profile

This profile runs detached for Docker, CI services, Kubernetes, and future hosted deployments. It
binds the configured MCP port and exposes the MCP endpoint plus
`/actuator/health/liveness` and `/actuator/health/readiness`. Liveness means the process/event loop
can serve; readiness additionally means configuration, durable-state adapters, policy/audit sinks,
and worker dispatch dependencies required for safe work are available. Neither response contains
configuration values or dependency diagnostics. Kubernetes supports only this profile.

Every MCP request requires OAuth 2.0/OIDC bearer authentication and authorization by role, user,
project, environment, action, and agent identity. Approval and append-only sanitized audit happen
below the transport. Rate and parallel limits apply before work dispatch. On SIGTERM, readiness
changes to unavailable, new work is rejected, in-flight requests drain until the configured
deadline, durable state is checkpointed at a safe boundary, descendants are cancelled, and the
original exit semantics are preserved.

## Sanitized discovery and readiness

`urn:codinglair:taf:mcp:schema:v1:discovery-readiness` is closed and bounded. It represents only
capability identifiers and versions, installed state, supported operation names, limitations,
configuration **keys**, configured instance aliases, ownership/isolation modes, and five stable
readiness states: `READY`, `DEGRADED`, `UNAVAILABLE`, `MISCONFIGURED`, and `UNKNOWN`. Check entries
contain only ID, type, and state. Credentials, tokens, secret values, endpoints containing user
information, receipt handles, raw payloads, native provider metadata, exception text, and policy
internals are not representable. Large or detailed evidence is returned through authorized bounded
resources, not this response.

## Threat review

| Threat | Required control / evidence |
| --- | --- |
| Profile confusion or attach/exec bypass | One required enum selection; reject dual/unknown selection and unsupported transport mechanisms |
| Protocol corruption or log leakage | STDIO stdout purity test; all logging and validation failures on stderr |
| Remote unauthenticated access | OIDC issuer/audience validation, authorization, approval, audit, and wrong-audience/expiry/scope tests |
| Secret or payload disclosure | References only; closed response schema and canary tests for credentials, receipt handles, and raw payloads |
| Resource exhaustion | Bounded schemas, rate/parallel limits, timeouts, request/body limits, and constrained workers |
| State loss or unsafe scaling | Durable state externalized before multiple replicas; safe-boundary checkpoint and shutdown tests |
| Workspace escape | Canonical scoped mount, read-only image/config, non-root process, allowlisted workflows |
| Mutable or incompatible image | Immutable release tag plus digest, OCI metadata, packaged compatibility tuple, SBOM and provenance |

## Versioning

The profile configuration schema is
`runtime-profile-config.schema.json`
(`urn:codinglair:taf:mcp:schema:v1:runtime-profile-config`); the compatibility schema is
`image-compatibility.schema.json` (`urn:codinglair:taf:mcp:schema:v1:image-compatibility`); and the
safe response is `discovery-readiness.schema.json`. Additive optional fields are compatible
within v1. Renaming a profile, changing precedence, adding a default profile, weakening security,
or making a supported deployment topology unsupported is breaking and requires a new major
contract with migration guidance.

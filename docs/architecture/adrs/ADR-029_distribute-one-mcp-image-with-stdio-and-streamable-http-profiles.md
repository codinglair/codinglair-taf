# ADR-029: Distribute One MCP Image with STDIO and Streamable HTTP Profiles

- **Status:** Accepted
- **Date:** 2026-09-14
- **Target release:** Codinglair TAF 1.2.0
- **Related requirements:** FR-MCP-013–FR-MCP-018; FR-PKG-003–FR-PKG-004; NFR-022–NFR-023

## Context

The MCP Server needs a supported image for local, CI, Kubernetes, and future hosted use. STDIO is a client-launched subprocess transport, whereas Kubernetes Services and detached containers require a network transport.

## Decision

Publish one Docker Hub image as `codinglair/codinglair-taf-mcp`. Immutable semantic release tags such as `1.2.0` are authoritative. `latest` is an optional movable convenience alias and is prohibited in reproducible CI and supported Kubernetes manifests.

The image has mutually exclusive `stdio` and `streamable-http` profiles:

- STDIO is supported for a local MCP client that launches `docker run --rm -i`. The client owns lifecycle, no MCP port is exposed, stdout contains protocol messages only, and logs use stderr. Pseudo-TTY allocation is not used.
- Streamable HTTP is used by detached Docker, CI services, Kubernetes, and future hosted deployments. It exposes a configurable MCP port and Spring Boot liveness/readiness health groups and enforces remote authentication, authorization, approval, audit, limits, and redaction.

`docker attach`, `kubectl attach`, and `kubectl exec` are operational mechanisms and are not supported MCP transports. Kubernetes officially supports Streamable HTTP only.

The supported Kubernetes profile defines Deployment, Service, probes, ConfigMap/Secret integration, explicit writable/persistent storage, non-root security context, service-account and network guidance, resource requests/limits, and graceful SIGTERM behavior. Authoritative job state, approvals, audit history, and retained results are externalized before multi-replica operation.

## Consequences

- One versioned artifact serves local and remote scenarios.
- STDIO remains simple but process-scoped and unsuitable as a cluster service.
- Kubernetes becomes an executable, qualified target rather than an aspirational claim.
- Image contract tests must verify signal handling, stdout purity, probes, sanitization, and immutable tagging.

## Alternatives considered

- Separate images per transport: rejected because application content would be duplicated.
- STDIO through Kubernetes attach/exec: rejected as fragile, privileged, non-service-oriented, and unsuitable for multiple clients.
- HTTP-only image: rejected because supported local STDIO clients would lose the simplest integration.


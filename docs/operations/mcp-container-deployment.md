# MCP container deployment for Codinglair TAF <!-- taf-version -->`1.2.0`

The official image is `codinglair/codinglair-taf-mcp`. Use immutable tag `1.2.0` for CI and
supported deployments:

```bash
docker pull codinglair/codinglair-taf-mcp:1.2.0
```

`latest` is a movable convenience alias. It is not reproducible and must not be used in CI or
Kubernetes. The image version identifies the compatible TAF release.

## Configuration and secrets

The image runs as UID/GID 10001 with a read-only root filesystem. Mount writable `/workspace` and
`/var/lib/taf-mcp`; mount non-secret configuration read-only at
`/etc/taf-mcp/application.yaml`. Supply only an opaque provider alias through
`TAF_MCP_SECRET_PROVIDER_REF`. Never bake credentials into the image or pass resolved secrets in
arguments, MCP requests, logs, or committed configuration.

`TAF_MCP_WORKSPACE` defaults to `/workspace`, `TAF_MCP_STATE_DIR` to `/var/lib/taf-mcp`, and
`TAF_MCP_PORT` to `8080` for HTTP. HTTP deployments configure `TAF_MCP_OIDC_ISSUER` and
`TAF_MCP_OIDC_AUDIENCE`; authorization, approval, audit, limits, and redaction remain mandatory.

## Local STDIO

Configure an MCP client to launch:

```bash
docker run --rm -i --network none \
  --read-only --user 10001:10001 \
  --tmpfs /tmp:rw,noexec,nosuid,size=128m \
  --mount type=bind,src="$PWD",dst=/workspace \
  codinglair/codinglair-taf-mcp:1.2.0 stdio
```

The client owns lifecycle. Keep stdin open, do not allocate a pseudo-TTY, and publish no port.
Protocol frames use stdout; logs use stderr. Detached STDIO, `docker attach`, `kubectl attach`, and
`kubectl exec` are not supported MCP transports.

## Docker and CI Streamable HTTP

```bash
docker run --rm --name taf-mcp \
  --read-only --user 10001:10001 -p 127.0.0.1:8080:8080 \
  -e TAF_MCP_OIDC_ISSUER=https://idp.example.test/realms/taf \
  -e TAF_MCP_OIDC_AUDIENCE=taf-mcp \
  -e TAF_MCP_SECRET_PROVIDER_REF=approved-provider-alias \
  --tmpfs /tmp:rw,noexec,nosuid,size=128m \
  --mount type=volume,src=taf-mcp-workspace,dst=/workspace \
  --mount type=volume,src=taf-mcp-state,dst=/var/lib/taf-mcp \
  codinglair/codinglair-taf-mcp:1.2.0 streamable-http
```

The MCP endpoint is `/mcp`. Probes are `/actuator/health/liveness` and
`/actuator/health/readiness`. CI pins `1.2.0` or a published digest, waits for readiness, uses a
short-lived OIDC workload identity, retains sanitized evidence, and always stops the container.

## Kubernetes and state

Kubernetes supports Streamable HTTP only. The supported Kind/Kustomize profile is under
`deploy/kind/mcp-reference`; see [`mcp-kubernetes-reference.md`](mcp-kubernetes-reference.md). Use
an immutable digest, ClusterIP or an environment-owned authenticated TLS boundary, probes,
ConfigMap/Secret references, non-root/read-only security, resource bounds, graceful termination,
and explicit writable storage.

The reference has one replica and process-local job, approval, audit, and result state. Bounded
`emptyDir` volumes are not durable; restart loses in-process state. Do not scale or claim HA or
durability until those authorities are externalized and qualified. NetworkPolicy requires a
policy-capable CNI for enforcement.

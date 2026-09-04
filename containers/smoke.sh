#!/usr/bin/env bash
set -euo pipefail

target="${1:?target is required}"
image="${2:?image is required}"
name="taf-${target}-smoke-${RANDOM}"
cleanup() { docker rm -f "$name" >/dev/null 2>&1 || true; }
trap cleanup EXIT

common=(--name "$name" --read-only --cap-drop ALL --security-opt no-new-privileges --pids-limit 128 --memory 768m --cpus 1 --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m)
if [[ "$target" == worker ]]; then
  docker run -d "${common[@]}" --network none \
    --tmpfs /workspace:rw,noexec,nosuid,nodev,size=256m,uid=10002,gid=10002,mode=0700 \
    --tmpfs /artifacts:rw,noexec,nosuid,nodev,size=128m,uid=10002,gid=10002,mode=0700 "$image"
else
  # Packaging smoke has no identity provider. Disable only the remote MCP binding;
  # the real OIDC boundary remains covered by MCP-009's Keycloak integration test.
  docker run -d "${common[@]}" -p 127.0.0.1::8080 "$image" --taf.mcp.http.enabled=false
fi

for _ in {1..30}; do
  status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$name")"
  [[ "$status" == healthy ]] && break
  [[ "$status" == unhealthy ]] && { docker logs "$name"; exit 1; }
  sleep 2
done
[[ "$(docker inspect --format '{{.State.Health.Status}}' "$name")" == healthy ]]
[[ "$(docker inspect --format '{{.Config.User}}' "$name")" != 0 ]]
[[ "$(docker inspect --format '{{.HostConfig.ReadonlyRootfs}}' "$name")" == true ]]
[[ "$(docker inspect --format '{{.HostConfig.SecurityOpt}}' "$name")" == '[no-new-privileges]' ]]
[[ "$(docker inspect --format '{{.HostConfig.CapDrop}}' "$name")" == '[ALL]' ]]

#!/usr/bin/env bash
set -euo pipefail

image="${1:?image is required}"
name="taf-mcp-smoke-${RANDOM}"
config_dir="$(mktemp -d)"
cleanup() {
  docker rm -f "$name" >/dev/null 2>&1 || true
  rm -rf "$config_dir"
}
trap cleanup EXIT

common=(--read-only --cap-drop ALL --security-opt no-new-privileges --pids-limit 128 --memory 768m --cpus 1 --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m)

launcher_java="$config_dir/java"
launcher_fixture="$(docker create "$image")"
docker cp "$launcher_fixture:/bin/echo" "$launcher_java"
docker rm "$launcher_fixture" >/dev/null
chmod 0555 "$launcher_java"

assert_launcher_port() {
  local expected="$1"
  local environment_port="$2"
  local cli_port="$3"
  local docker_options=()
  local launcher_options=()
  [[ "$environment_port" == '<unset>' ]] || docker_options=(-e "TAF_MCP_PORT=$environment_port")
  [[ "$cli_port" == '<unset>' ]] || launcher_options=(--port "$cli_port")
  docker run --rm --read-only --mount "type=bind,src=$launcher_java,dst=/opt/java/openjdk/bin/java,readonly" \
    "${docker_options[@]}" "$image" streamable-http "${launcher_options[@]}" \
    | grep -Fq -- "--server.port=$expected"
}

assert_invalid_launcher_port() {
  local invalid_port="$1"
  if docker run --rm --read-only --mount "type=bind,src=$launcher_java,dst=/opt/java/openjdk/bin/java,readonly" \
    -e "TAF_MCP_PORT=$invalid_port" "$image" streamable-http \
    >"$config_dir/launcher.out" 2>"$config_dir/launcher.err"; then
    echo "invalid launcher port unexpectedly succeeded: $invalid_port" >&2
    exit 1
  fi
  grep -Fq 'port must be between 1 and 65535' "$config_dir/launcher.err"
  ! grep -Eiq 'password|token|secret=' "$config_dir/launcher.err"
}

# Exercise the packaged launcher while replacing only Java after validation, so the test observes
# the exact argument and environment precedence used by the runtime image.
assert_launcher_port 8080 '<unset>' '<unset>'
assert_launcher_port 8080 '' '<unset>'
assert_launcher_port 8080 8080 '<unset>'
assert_launcher_port 18080 18080 '<unset>'
assert_launcher_port 19090 '<unset>' 19090
assert_launcher_port 19090 18080 19090
for invalid_port in 0 -1 65536 invalid; do
  assert_invalid_launcher_port "$invalid_port"
done

if docker run --rm "$image" unknown 2>"$config_dir/error"; then
  echo "unknown profile unexpectedly succeeded" >&2
  exit 1
fi
grep -q 'unsupported profile' "$config_dir/error"
! grep -Eiq 'password|token|secret=' "$config_dir/error"

docker run -d --name "$name" "${common[@]}" -p 127.0.0.1::8080 \
  --mount "type=bind,src=$(pwd)/containers/mcp/application-smoke.yaml,dst=/etc/taf-mcp/application.yaml,readonly" \
  "$image" streamable-http
for _ in {1..30}; do
  port="$(docker port "$name" 8080/tcp | sed 's/.*://')"
  if curl --fail --silent "http://127.0.0.1:${port}/actuator/health/liveness" >/dev/null; then
    break
  fi
  [[ "$(docker inspect --format '{{.State.Running}}' "$name")" == true ]] || {
    docker logs "$name" >&2
    exit 1
  }
  sleep 2
done
curl --fail --silent "http://127.0.0.1:${port}/actuator/health/readiness" >/dev/null
[[ "$(docker inspect --format '{{.Config.User}}' "$name")" == 10001:10001 ]]
[[ "$(docker inspect --format '{{.HostConfig.ReadonlyRootfs}}' "$name")" == true ]]

docker stop --time 30 "$name" >/dev/null
[[ "$(docker inspect --format '{{.State.ExitCode}}' "$name")" == 143 ]]
docker rm "$name" >/dev/null
name=""

printf '%s\n' \
  '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"image-smoke","version":"1"}}}' \
  '{"jsonrpc":"2.0","method":"notifications/initialized"}' |
  docker run --rm -i "${common[@]}" --network none "$image" stdio >"$config_dir/stdio.out" 2>"$config_dir/stdio.err"
grep -q '"id":1' "$config_dir/stdio.out"
! grep -Eiq 'started|spring|password|token|secret=' "$config_dir/stdio.out"

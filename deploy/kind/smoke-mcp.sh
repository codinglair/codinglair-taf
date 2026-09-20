#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CLUSTER_NAME="${TAF_KIND_CLUSTER:-codinglair-taf-mcp}"
CONTEXT="kind-$CLUSTER_NAME"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

run_authenticated_smoke() {
  kubectl --context "$CONTEXT" -n taf-system delete job taf-mcp-smoke --ignore-not-found >/dev/null
  sed 's#http://taf-control-plane:8080/mcp#http://taf-mcp:8080/mcp#g' \
    "$ROOT_DIR/deploy/kind/smoke-client.yaml" >"$TMP_DIR/smoke-client.yaml"
  kubectl --context "$CONTEXT" apply -f "$TMP_DIR/smoke-client.yaml" >/dev/null
  if ! kubectl --context "$CONTEXT" -n taf-system wait --for=condition=complete job/taf-mcp-smoke --timeout=150s; then
    kubectl --context "$CONTEXT" -n taf-system describe job taf-mcp-smoke >&2 || true
    kubectl --context "$CONTEXT" -n taf-system logs job/taf-mcp-smoke --tail=200 >&2 || true
    return 1
  fi
  kubectl --context "$CONTEXT" -n taf-system logs job/taf-mcp-smoke >"$TMP_DIR/smoke.log"
  grep -Fq 'MCP discovery and execution smoke passed' "$TMP_DIR/smoke.log"
  grep -Fq 'request rejected with HTTP 401; response body withheld' "$TMP_DIR/smoke.log"
  echo 'MCP discovery and execution smoke passed'
  echo 'Unauthenticated MCP request rejected with HTTP 401; response body withheld'
}

run_authenticated_smoke
old_uid="$(kubectl --context "$CONTEXT" -n taf-system get pod -l app.kubernetes.io/name=taf-mcp -o jsonpath='{.items[0].metadata.uid}')"
old_pod="$(kubectl --context "$CONTEXT" -n taf-system get pod -l app.kubernetes.io/name=taf-mcp -o jsonpath='{.items[0].metadata.name}')"
kubectl --context "$CONTEXT" -n taf-system logs -f "$old_pod" >"$TMP_DIR/shutdown.log" 2>&1 &
log_pid=$!
kubectl --context "$CONTEXT" -n taf-system delete pod "$old_pod" --wait=true --timeout=40s >/dev/null
wait "$log_pid" || true
kubectl --context "$CONTEXT" -n taf-system rollout status deployment/taf-mcp --timeout=240s
new_uid="$(kubectl --context "$CONTEXT" -n taf-system get pod -l app.kubernetes.io/name=taf-mcp -o jsonpath='{.items[0].metadata.uid}')"
test "$old_uid" != "$new_uid"
grep -Fq 'Commencing graceful shutdown' "$TMP_DIR/shutdown.log"
grep -Fq 'Graceful shutdown complete' "$TMP_DIR/shutdown.log"
echo 'Observed application markers: Commencing graceful shutdown; Graceful shutdown complete'
run_authenticated_smoke
echo 'Authenticated MCP, graceful shutdown, pod replacement, and service recovery passed'


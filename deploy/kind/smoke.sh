#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CLUSTER_NAME="${TAF_KIND_CLUSTER:-codinglair-taf}"
KUBE_CONTEXT="kind-$CLUSTER_NAME"
MONGO_MODE="${TAF_KIND_MONGODB_MODE:-internal}"

stage() {
  printf 'TAF Kind smoke stage: %s\n' "$1"
}

smoke_pod_name() {
  kubectl --context "$KUBE_CONTEXT" -n taf-system get pods \
    -l job-name=taf-mcp-smoke -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true
}

collect_failure_diagnostics() {
  local smoke_pod
  smoke_pod="$(smoke_pod_name)"

  echo 'TAF Kind smoke diagnostics: Job' >&2
  kubectl --context "$KUBE_CONTEXT" -n taf-system describe job taf-mcp-smoke >&2 || true
  if [[ -n "$smoke_pod" ]]; then
    echo "TAF Kind smoke diagnostics: pod=$smoke_pod" >&2
    kubectl --context "$KUBE_CONTEXT" -n taf-system get pod "$smoke_pod" \
      -o 'custom-columns=NAME:.metadata.name,PHASE:.status.phase,REASON:.status.reason' >&2 || true
    kubectl --context "$KUBE_CONTEXT" -n taf-system describe pod "$smoke_pod" >&2 || true
    kubectl --context "$KUBE_CONTEXT" -n taf-system logs "$smoke_pod" -c client --tail=300 >&2 || true
    kubectl --context "$KUBE_CONTEXT" -n taf-system get pod "$smoke_pod" -o jsonpath=$'{range .status.containerStatuses[*]}container={.name} reason={.state.terminated.reason} exitCode={.state.terminated.exitCode} message={.state.terminated.message}{"\\n"}{end}' >&2 || true
  else
    echo 'TAF Kind smoke diagnostics: smoke pod not found' >&2
  fi

  echo 'TAF Kind smoke diagnostics: workload status' >&2
  kubectl --context "$KUBE_CONTEXT" -n taf-system get pods,deployments,statefulsets,jobs,pvc -o wide >&2 || true
  echo 'TAF Kind smoke diagnostics: MCP control-plane logs' >&2
  kubectl --context "$KUBE_CONTEXT" -n taf-system logs deployment/taf-control-plane --all-pods=true --all-containers=true --tail=300 >&2 || true
  kubectl --context "$KUBE_CONTEXT" -n taf-system logs deployment/taf-control-plane --all-pods=true --all-containers=true --previous --tail=300 >&2 || true
  echo 'TAF Kind smoke diagnostics: Keycloak logs' >&2
  kubectl --context "$KUBE_CONTEXT" -n taf-system logs deployment/taf-keycloak --all-pods=true --all-containers=true --tail=200 >&2 || true
  echo 'TAF Kind smoke diagnostics: recent events' >&2
  kubectl --context "$KUBE_CONTEXT" -n taf-system get events --sort-by='.metadata.creationTimestamp' | tail -n 200 >&2 || true
}

on_error() {
  local exit_code="$1"
  local failing_line="$2"
  local failing_command="$3"
  trap - ERR
  set +e
  printf 'TAF Kind smoke failed: exit=%s line=%s command=%q\n' \
    "$exit_code" "$failing_line" "$failing_command" >&2
  collect_failure_diagnostics
  exit "$exit_code"
}

trap 'on_error "$?" "$LINENO" "$BASH_COMMAND"' ERR

run_mcp_smoke() {
  stage 'smoke Job creation'
  kubectl --context "$KUBE_CONTEXT" -n taf-system delete job taf-mcp-smoke --ignore-not-found >/dev/null
  kubectl --context "$KUBE_CONTEXT" apply -f "$ROOT_DIR/deploy/kind/smoke-client.yaml" >/dev/null
  stage 'smoke Job wait (OIDC/authentication, MCP initialize, MCP tools/list, MCP tool/get execution)'
  for _ in $(seq 1 75); do
    if [[ "$(kubectl --context "$KUBE_CONTEXT" -n taf-system get job taf-mcp-smoke -o jsonpath='{.status.succeeded}' 2>/dev/null)" == 1 ]]; then
      if ! kubectl --context "$KUBE_CONTEXT" -n taf-system logs job/taf-mcp-smoke | grep -Fq 'MCP discovery and execution smoke passed'; then
        echo 'TAF MCP smoke Job completed without its success marker' >&2
        return 41
      fi
      stage 'smoke Job wait complete'
      return
    fi
    if [[ "$(kubectl --context "$KUBE_CONTEXT" -n taf-system get job taf-mcp-smoke -o jsonpath='{.status.failed}' 2>/dev/null)" == 1 ]]; then
      echo 'TAF MCP smoke Job failed' >&2
      kubectl --context "$KUBE_CONTEXT" -n taf-system logs job/taf-mcp-smoke >&2 || true
      return 1
    fi
    sleep 2
  done
  echo 'Timed out waiting for TAF MCP smoke Job' >&2
  kubectl --context "$KUBE_CONTEXT" -n taf-system logs job/taf-mcp-smoke >&2 || true
  kubectl --context "$KUBE_CONTEXT" -n taf-system describe job taf-mcp-smoke >&2 || true
  return 1
}

run_mcp_smoke
stage 'control-plane restart validation: capture original pod'
old_uid="$(kubectl --context "$KUBE_CONTEXT" -n taf-system get pod -l app.kubernetes.io/name=taf-control-plane -o jsonpath='{.items[0].metadata.uid}')"

if [[ "$MONGO_MODE" == internal ]]; then
  stage 'Mongo persistence/PVC validation: write sentinel'
  mongo_pod="$(kubectl --context "$KUBE_CONTEXT" -n taf-system get pod -l app.kubernetes.io/name=taf-mongodb -o jsonpath='{.items[0].metadata.name}')"
  kubectl --context "$KUBE_CONTEXT" -n taf-system exec "$mongo_pod" -- sh -ec \
    'mongosh --quiet --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --eval "db.getSiblingDB(\"taf-kind-smoke\").restart.replaceOne({_id: 1}, {value: \"retained\"}, {upsert: true})"' >/dev/null
  kubectl --context "$KUBE_CONTEXT" -n taf-system delete pod "$mongo_pod" --wait=true >/dev/null
  kubectl --context "$KUBE_CONTEXT" -n taf-system rollout status statefulset/taf-mongodb --timeout=180s
  stage 'Mongo persistence/PVC validation: verify sentinel after restart'
  mongo_pod="$(kubectl --context "$KUBE_CONTEXT" -n taf-system get pod -l app.kubernetes.io/name=taf-mongodb -o jsonpath='{.items[0].metadata.name}')"
  kubectl --context "$KUBE_CONTEXT" -n taf-system exec "$mongo_pod" -- sh -ec \
    'test "$(mongosh --quiet --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --eval "db.getSiblingDB(\"taf-kind-smoke\").restart.findOne({_id: 1}).value")" = retained'
fi

stage 'control-plane restart validation: replace pod'
kubectl --context "$KUBE_CONTEXT" -n taf-system delete pod -l app.kubernetes.io/name=taf-control-plane --wait=true >/dev/null
kubectl --context "$KUBE_CONTEXT" -n taf-system rollout status deployment/taf-control-plane --timeout=180s
new_uid="$(kubectl --context "$KUBE_CONTEXT" -n taf-system get pod -l app.kubernetes.io/name=taf-control-plane -o jsonpath='{.items[0].metadata.uid}')"
test "$old_uid" != "$new_uid"
stage 'control-plane restart validation: authenticated MCP recheck'
run_mcp_smoke
echo 'Pod restart, persistence, and MCP smoke passed'

#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CLUSTER_NAME="${TAF_KIND_CLUSTER:-codinglair-taf-mcp}"
POM_VERSION="$(sed -n 's:.*<revision>\([^<]*\)</revision>.*:\1:p' "$ROOT_DIR/pom.xml" | head -n 1)"
IMAGE_VERSION="${TAF_IMAGE_VERSION:-$POM_VERSION}"
IMAGE="codinglair/codinglair-taf-mcp:${IMAGE_VERSION}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

require() { command -v "$1" >/dev/null 2>&1 || { echo "Required command not found: $1" >&2; exit 2; }; }
require docker
require kind
require kubectl
require openssl

rollout_or_diagnose() {
  local workload="$1"
  local timeout="$2"
  local selector="$3"
  if kubectl --context "$context" -n taf-system rollout status "$workload" --timeout="$timeout"; then
    return
  fi
  echo "TAF MCP Kind rollout failed: $workload" >&2
  kubectl --context "$context" -n taf-system get pods -o wide >&2 || true
  kubectl --context "$context" -n taf-system describe pods -l "$selector" >&2 || true
  kubectl --context "$context" -n taf-system get events --sort-by=.lastTimestamp >&2 || true
  return 1
}

if [[ ! "$IMAGE_VERSION" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$ ]]; then
  echo "TAF image version is not a valid container tag" >&2
  exit 2
fi
if [[ "${TAF_KIND_SKIP_IMAGE_BUILD:-false}" != true ]]; then
  (cd "$ROOT_DIR" && VERSION="$IMAGE_VERSION" docker buildx build --load --file containers/mcp/Dockerfile --tag "$IMAGE" .)
fi
docker image inspect "$IMAGE" >/dev/null

if ! kind get clusters | grep -Fxq "$CLUSTER_NAME"; then
  kind create cluster --name "$CLUSTER_NAME" --config "$ROOT_DIR/deploy/kind/cluster.yaml" --wait 120s
fi
context="kind-$CLUSTER_NAME"
kind load docker-image --name "$CLUSTER_NAME" "$IMAGE"
kind_node="$(kind get nodes --name "$CLUSTER_NAME" | head -n 1)"
normalized_image="docker.io/$IMAGE"
if ! docker exec "$kind_node" ctr -n k8s.io images list | awk -v image="$normalized_image" '$1 == image {found=1} END {exit !found}'; then
  echo "Loaded MCP image reference is absent from Kind: $normalized_image" >&2
  exit 2
fi
kubectl --context "$context" apply -f "$ROOT_DIR/deploy/kind/mcp-reference/namespace.yaml" >/dev/null

admin_password="$(openssl rand -hex 24)"
smoke_password="$(openssl rand -hex 24)"
printf 'admin-username=%s\nadmin-password=%s\n' taf-admin "$admin_password" >"$TMP_DIR/keycloak.env"
printf 'username=%s\npassword=%s\n' taf-kind-smoke "$smoke_password" >"$TMP_DIR/smoke.env"
printf 'secret-provider-ref=%s\n' kind-reference >"$TMP_DIR/mcp.env"
sed "s/@SMOKE_PASSWORD@/$smoke_password/g" "$ROOT_DIR/deploy/kind/realm-template.json" >"$TMP_DIR/taf-realm.json"
kubectl --context "$context" -n taf-system create secret generic taf-keycloak-credentials --from-env-file="$TMP_DIR/keycloak.env" --dry-run=client -o yaml | kubectl --context "$context" apply -f - >/dev/null
kubectl --context "$context" -n taf-system create secret generic taf-keycloak-realm --from-file=taf-realm.json="$TMP_DIR/taf-realm.json" --dry-run=client -o yaml | kubectl --context "$context" apply -f - >/dev/null
kubectl --context "$context" -n taf-system create secret generic taf-smoke-credentials --from-env-file="$TMP_DIR/smoke.env" --dry-run=client -o yaml | kubectl --context "$context" apply -f - >/dev/null
kubectl --context "$context" -n taf-system create secret generic taf-mcp-runtime --from-env-file="$TMP_DIR/mcp.env" --dry-run=client -o yaml | kubectl --context "$context" apply -f - >/dev/null

kubectl --context "$context" -n taf-system apply -f "$ROOT_DIR/deploy/kind/mcp-reference/keycloak.yaml" >/dev/null
rollout_or_diagnose deployment/taf-keycloak 180s app.kubernetes.io/name=taf-keycloak
# `kind load docker-image` imports the local image under this exact tag. A registry digest is not
# registered as an equivalent local alias, so the local Never-pull profile must request the tag it
# loaded. The checked-in digest token remains the registry/release deployment contract.
kubectl kustomize "$ROOT_DIR/deploy/kind/mcp-reference" \
  | sed "s#codinglair/codinglair-taf-mcp@sha256:IMAGE_DIGEST#$IMAGE#g" >"$TMP_DIR/mcp-reference.yaml"
grep -Fq "image: $IMAGE" "$TMP_DIR/mcp-reference.yaml"
grep -Fq 'imagePullPolicy: Never' "$TMP_DIR/mcp-reference.yaml"
! grep -Fq 'IMAGE_DIGEST' "$TMP_DIR/mcp-reference.yaml"
! grep -q '^kind: Secret$' "$TMP_DIR/mcp-reference.yaml"
kubectl --context "$context" apply -f "$TMP_DIR/mcp-reference.yaml" >/dev/null
applied_image="$(kubectl --context "$context" -n taf-system get deployment taf-mcp -o jsonpath='{.spec.template.spec.containers[?(@.name=="mcp")].image}')"
[[ "$applied_image" == "$IMAGE" ]] || { echo "Applied MCP image mismatch: expected=$IMAGE actual=$applied_image" >&2; exit 2; }
rollout_or_diagnose deployment/taf-mcp 240s app.kubernetes.io/name=taf-mcp
echo "TAF MCP Kind deployment is ready with locally loaded image $IMAGE"

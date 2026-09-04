#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CLUSTER_NAME="${TAF_KIND_CLUSTER:-codinglair-taf}"
MONGO_MODE="${TAF_KIND_MONGODB_MODE:-internal}"
POM_VERSION="$(sed -n 's:.*<revision>\([^<]*\)</revision>.*:\1:p' "$ROOT_DIR/pom.xml" | head -n 1)"
IMAGE_VERSION="${TAF_IMAGE_VERSION:-$POM_VERSION}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

require() { command -v "$1" >/dev/null 2>&1 || { echo "Required command not found: $1" >&2; exit 2; }; }
random_value() { openssl rand -hex 24; }

require docker
require kind
require kubectl
require openssl

if [[ -z "$POM_VERSION" || "$POM_VERSION" == *'${'* ]]; then
  echo "The root pom.xml must define a literal revision property" >&2
  exit 2
fi
if [[ ! "$IMAGE_VERSION" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$ ]]; then
  echo "TAF image version is not a valid container tag: $IMAGE_VERSION" >&2
  exit 2
fi

case "$MONGO_MODE" in
  internal|external) ;;
  *) echo "TAF_KIND_MONGODB_MODE must be internal or external" >&2; exit 2 ;;
esac
if [[ "$MONGO_MODE" == external && -z "${TAF_EXTERNAL_MONGODB_URI:-}" ]]; then
  echo "TAF_EXTERNAL_MONGODB_URI is required in external mode" >&2
  exit 2
fi

if ! kind get clusters | grep -Fxq "$CLUSTER_NAME"; then
  kind create cluster --name "$CLUSTER_NAME" --config "$ROOT_DIR/deploy/kind/cluster.yaml" --wait 120s
fi
KUBE_CONTEXT="kind-$CLUSTER_NAME"

if [[ "${TAF_KIND_SKIP_IMAGE_BUILD:-false}" != true ]]; then
  (cd "$ROOT_DIR" && VERSION="$IMAGE_VERSION" docker buildx bake --load)
fi
kind load docker-image --name "$CLUSTER_NAME" \
  "codinglair/taf-control-plane:$IMAGE_VERSION" "codinglair/taf-worker:$IMAGE_VERSION"

kubectl --context "$KUBE_CONTEXT" apply -f "$ROOT_DIR/deploy/kind/base/namespace.yaml" >/dev/null

admin_password="$(random_value)"
smoke_password="$(random_value)"
mongo_password="$(random_value)"
printf 'admin-username=%s\nadmin-password=%s\n' 'taf-admin' "$admin_password" >"$TMP_DIR/keycloak.env"
printf 'username=%s\npassword=%s\n' 'taf-kind-smoke' "$smoke_password" >"$TMP_DIR/smoke.env"
printf 'username=%s\npassword=%s\n' 'taf-root' "$mongo_password" >"$TMP_DIR/mongodb.env"
sed "s/@SMOKE_PASSWORD@/$smoke_password/g" "$ROOT_DIR/deploy/kind/realm-template.json" >"$TMP_DIR/taf-realm.json"

kubectl --context "$KUBE_CONTEXT" -n taf-system delete secret \
  taf-keycloak-credentials taf-keycloak-realm taf-smoke-credentials \
  taf-mongodb-credentials taf-mongodb-connection --ignore-not-found >/dev/null
kubectl --context "$KUBE_CONTEXT" -n taf-system create secret generic taf-keycloak-credentials --from-env-file="$TMP_DIR/keycloak.env" >/dev/null
kubectl --context "$KUBE_CONTEXT" -n taf-system create secret generic taf-keycloak-realm --from-file=taf-realm.json="$TMP_DIR/taf-realm.json" >/dev/null
kubectl --context "$KUBE_CONTEXT" -n taf-system create secret generic taf-smoke-credentials --from-env-file="$TMP_DIR/smoke.env" >/dev/null
kubectl --context "$KUBE_CONTEXT" -n taf-system create secret generic taf-mongodb-credentials --from-env-file="$TMP_DIR/mongodb.env" >/dev/null

if [[ "$MONGO_MODE" == internal ]]; then
  printf 'uri=mongodb://taf-root:%s@taf-mongodb:27017/taf-context?authSource=admin\n' "$mongo_password" >"$TMP_DIR/mongodb-connection.env"
else
  printf 'uri=%s\n' "$TAF_EXTERNAL_MONGODB_URI" >"$TMP_DIR/mongodb-connection.env"
fi
kubectl --context "$KUBE_CONTEXT" -n taf-system create secret generic taf-mongodb-connection --from-env-file="$TMP_DIR/mongodb-connection.env" >/dev/null

# The HTTP transport resolves the issuer metadata while constructing its fail-closed JWT decoder.
# Create and verify the reference IdP before the overlay can create the control-plane Deployment.
echo 'TAF Kind bootstrap stage: deploy Keycloak identity provider'
kubectl --context "$KUBE_CONTEXT" -n taf-system apply -f "$ROOT_DIR/deploy/kind/base/keycloak.yaml" >/dev/null
kubectl --context "$KUBE_CONTEXT" -n taf-system rollout status deployment/taf-keycloak --timeout=180s

echo 'TAF Kind bootstrap stage: deploy MCP and Runtime workloads'
kubectl kustomize "$ROOT_DIR/deploy/kind/overlays/$MONGO_MODE-mongodb" \
  | sed "s/:project-version/:$IMAGE_VERSION/g" >"$TMP_DIR/taf-kind.yaml"
kubectl --context "$KUBE_CONTEXT" apply -f "$TMP_DIR/taf-kind.yaml"
kubectl --context "$KUBE_CONTEXT" -n taf-system rollout status deployment/taf-control-plane --timeout=180s
kubectl --context "$KUBE_CONTEXT" -n taf-system rollout status deployment/taf-worker --timeout=120s
if [[ "$MONGO_MODE" == internal ]]; then
  kubectl --context "$KUBE_CONTEXT" -n taf-system rollout status statefulset/taf-mongodb --timeout=180s
fi
echo "TAF Kind deployment is ready in context $KUBE_CONTEXT"

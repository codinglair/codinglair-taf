#!/usr/bin/env bash
set -euo pipefail

CLUSTER_NAME="${TAF_KIND_CLUSTER:-codinglair-taf}"
if kind get clusters | grep -Fxq "$CLUSTER_NAME"; then
  kind delete cluster --name "$CLUSTER_NAME"
fi
echo "TAF Kind cluster $CLUSTER_NAME is absent"

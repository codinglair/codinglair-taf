#!/usr/bin/env sh
set -eu

GITLEAKS_IMAGE="ghcr.io/gitleaks/gitleaks:v8.30.1@sha256:c00b6bd0aeb3071cbcb79009cb16a60dd9e0a7c60e2be9ab65d25e6bc8abbb7f"
MODE="${1:-}"

case "$MODE" in
  pr)
    BASE_SHA="${2:-}"
    HEAD_SHA="${3:-}"
    test -n "$BASE_SHA" && test -n "$HEAD_SHA"
    git cat-file -e "${BASE_SHA}^{commit}"
    git cat-file -e "${HEAD_SHA}^{commit}"
    test -n "$(git rev-list "${BASE_SHA}..${HEAD_SHA}")"
    LOG_OPTS="${BASE_SHA}..${HEAD_SHA}"
    ;;
  history)
    LOG_OPTS="--all"
    ;;
  *)
    echo "Usage: $0 pr BASE_SHA HEAD_SHA | history" >&2
    exit 2
    ;;
esac

docker run --rm --network none \
  --mount "type=bind,source=$(pwd),target=/repo,readonly" \
  --workdir /repo \
  "$GITLEAKS_IMAGE" \
  git --no-banner --redact=100 --config /repo/.gitleaks.toml --log-opts="$LOG_OPTS" /repo

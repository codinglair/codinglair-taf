#!/usr/bin/env bash
set -euo pipefail

evidence_root="${1:?evidence root is required}"
[[ -d "$evidence_root" ]] || {
  echo "Evidence root does not exist: $evidence_root" >&2
  exit 64
}

report="$evidence_root/leak-scan.txt"
temporary_report="$(mktemp)"
filtered_report="$(mktemp)"
trap 'rm -f "$temporary_report" "$filtered_report"' EXIT

# Retained qualification output must never contain credential material, message receipt handles,
# raw authorization headers, or the explicit canaries used by the qualification workflow.
pattern='(Authorization:[[:space:]]*Bearer|receipt[_-]?[Hh]andle|password[[:space:]]*[=:][[:space:]]*[^${<[:space:]]|secret[[:space:]]*[=:][[:space:]]*[^/${<[:space:]]|token[[:space:]]*[=:][[:space:]]*[^${<[:space:]])'
grep -RInaE --include='*.java' --include='*.json' --include='*.log' --include='*.md' \
  --include='*.out' --include='*.properties' --include='*.txt' --include='*.xml' \
  --include='*.yaml' --include='*.yml' "$pattern" "$evidence_root" >"$temporary_report" || true
# Opaque secret references and Kubernetes Secret selectors are the intended safe configuration
# representation; values and inline assignments remain prohibited.
grep -Ev 'secret://|secretKeyRef:|valueFrom:' "$temporary_report" >"$filtered_report" || true
if [[ -s "$filtered_report" ]]; then
  sed -E 's/=.*/=<redacted>/' "$filtered_report" >&2
  echo 'VER-120-002 leak scan failed' >&2
  exit 1
fi

for canary in "${@:2}"; do
  [[ -n "$canary" ]] || continue
  if grep -RIlF --include='*.java' --include='*.json' --include='*.log' --include='*.md' \
      --include='*.out' --include='*.properties' --include='*.txt' --include='*.xml' \
      --include='*.yaml' --include='*.yml' -- "$canary" "$evidence_root" | grep -q .; then
    echo 'VER-120-002 leak scan found a prohibited canary value' >&2
    exit 1
  fi
done

printf '%s\n' \
  'VER-120-002 leak scan: PASS' \
  'Surfaces: retained response, log, report, configuration, example, and evidence text files' \
  'Checks: authorization values, credential assignments, receipt handles, and supplied canaries' \
  >"$report"

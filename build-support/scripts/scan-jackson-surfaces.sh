#!/usr/bin/env bash
set -euo pipefail

readonly TRIVY_IMAGE="aquasec/trivy:0.72.0"
readonly STAGING_REPOSITORY="${1:-target/staging-repository}"
readonly EVIDENCE_DIRECTORY="${2:-target/dep-132-001}"
readonly WORKSPACE_DOCKER_PATH="$(pwd -W 2>/dev/null || pwd)"

if [[ ! -d "$STAGING_REPOSITORY" ]]; then
  echo "Staging repository not found: $STAGING_REPOSITORY" >&2
  exit 2
fi
command -v docker >/dev/null || { echo "Docker is required to run pinned Trivy $TRIVY_IMAGE" >&2; exit 2; }

summarize_findings() {
  local report="$1"
  if command -v python3 >/dev/null && python3 --version >/dev/null 2>&1; then
    python3 -c 'import json,sys; data=json.load(open(sys.argv[1], encoding="utf-8")); findings=[v for r in data.get("Results",[]) for v in (r.get("Vulnerabilities") or [])]; jackson=[v for v in findings if "jackson" in (v.get("PkgName") or "").lower()]; print(str(len(jackson))+"\t"+str(len(findings)-len(jackson)))' "$report"
  elif command -v pwsh >/dev/null; then
    TRIVY_REPORT_PATH="$report" pwsh -NoProfile -Command '$data = Get-Content -Raw -LiteralPath $env:TRIVY_REPORT_PATH | ConvertFrom-Json; $findings = @($data.Results | ForEach-Object { @($_.Vulnerabilities) }); $jackson = @($findings | Where-Object { $_.PkgName -like "*jackson*" }); "{0}`t{1}" -f $jackson.Count, ($findings.Count - $jackson.Count)'
  else
    echo "Python 3 or PowerShell is required to summarize Trivy JSON" >&2
    return 2
  fi
}

mkdir -p "$EVIDENCE_DIRECTORY/scans"
printf 'surface\tsbom_sha256\tjackson_components\thigh_critical_jackson\thigh_critical_unrelated\treport\n' \
  > "$EVIDENCE_DIRECTORY/surface-evidence.tsv"
docker run --rm "$TRIVY_IMAGE" --version > "$EVIDENCE_DIRECTORY/trivy-version.txt"

scan_failures=0
surface_count=0
while IFS= read -r -d '' sbom; do
  relative="${sbom#"$STAGING_REPOSITORY"/}"
  surface="${relative%-cyclonedx.json}"
  report="$EVIDENCE_DIRECTORY/scans/${surface//\//_}-trivy.json"
  mkdir -p "$(dirname "$report")"

  MSYS_NO_PATHCONV=1 docker run --rm -v "${WORKSPACE_DOCKER_PATH}:/work" \
      -v taf-trivy-cache:/root/.cache/trivy -w /work "$TRIVY_IMAGE" \
      sbom --scanners vuln --severity HIGH,CRITICAL --exit-code 0 --format json \
      --output "$report" "$sbom"

  sbom_sha="$(sha256sum "$sbom" | cut -d ' ' -f 1)"
  jackson_components="$(grep -Eoc '\"group\"[[:space:]]*:[[:space:]]*\"(com.fasterxml.jackson.(core|dataformat)|tools.jackson.(core|dataformat))\"' "$sbom" || true)"
  counts="$(summarize_findings "$report")"
  jackson_findings="${counts%%$'\t'*}"
  unrelated_findings="${counts#*$'\t'}"
  printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$surface" "$sbom_sha" "$jackson_components" \
    "$jackson_findings" "$unrelated_findings" "$report" >> "$EVIDENCE_DIRECTORY/surface-evidence.tsv"
  surface_count=$((surface_count + 1))
  if (( jackson_findings > 0 )); then
    scan_failures=$((scan_failures + jackson_findings))
  fi
done < <(find "$STAGING_REPOSITORY" -type f -name '*-cyclonedx.json' -print0 | sort -z)

if (( surface_count == 0 )); then
  echo "No staged CycloneDX SBOMs found under $STAGING_REPOSITORY" >&2
  exit 2
fi
if (( scan_failures > 0 )); then
  echo "Rejected $scan_failures unapproved HIGH/CRITICAL Jackson finding(s); unrelated findings remain disclosed in retained JSON." >&2
  exit 1
fi
echo "Scanned $surface_count staged publication surfaces; no HIGH/CRITICAL Jackson findings."

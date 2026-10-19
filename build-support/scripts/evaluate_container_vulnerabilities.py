#!/usr/bin/env python3
"""Classify raw Trivy findings against narrow, expiring distribution exceptions."""

from __future__ import annotations

import json
import sys
from datetime import date
from pathlib import Path


POLICY = Path("build-support/security/container-vulnerability-exceptions.json")
REQUIRED_FIELDS = (
    "vulnerabilityId",
    "package",
    "installedVersion",
    "containingArtifact",
    "containingVersion",
    "packagePath",
    "distribution",
    "owner",
    "approvedOn",
    "reviewOn",
    "expiresOn",
    "trigger",
)


def load_json(path: Path) -> object:
    if not path.is_file() or path.stat().st_size == 0:
        raise ValueError(f"required vulnerability evidence is missing: {path}")
    with path.open(encoding="utf-8") as source:
        return json.load(source)


def load_policy(path: Path = POLICY) -> list[dict[str, str]]:
    document = load_json(path)
    if not isinstance(document, dict) or document.get("schemaVersion") != 1:
        raise ValueError("unsupported vulnerability exception registry schema")
    exceptions = document.get("exceptions")
    if not isinstance(exceptions, list):
        raise ValueError("vulnerability exception registry must contain an exceptions array")
    for exception in exceptions:
        if not isinstance(exception, dict):
            raise ValueError("vulnerability exception entries must be objects")
        for field in REQUIRED_FIELDS:
            if not isinstance(exception.get(field), str) or not exception[field]:
                raise ValueError(f"vulnerability exception field is required: {field}")
        for field in ("approvedOn", "reviewOn", "expiresOn"):
            date.fromisoformat(exception[field])
    return exceptions


def findings(document: object) -> list[dict[str, str]]:
    if not isinstance(document, dict):
        raise ValueError("Trivy report must be a JSON object")
    flattened: list[dict[str, str]] = []
    for result in document.get("Results") or []:
        for vulnerability in result.get("Vulnerabilities") or []:
            layer = vulnerability.get("Layer") or {}
            flattened.append(
                {
                    "target": result.get("Target") or "",
                    "vulnerabilityId": vulnerability.get("VulnerabilityID") or "",
                    "package": vulnerability.get("PkgName") or "",
                    "installedVersion": vulnerability.get("InstalledVersion") or "",
                    "fixedVersion": vulnerability.get("FixedVersion") or "",
                    "severity": vulnerability.get("Severity") or "",
                    "packagePath": vulnerability.get("PkgPath") or "",
                    "layerDigest": layer.get("Digest") or "",
                    "layerDiffId": layer.get("DiffID") or "",
                    "title": vulnerability.get("Title") or "",
                }
            )
    return flattened


def matches(finding: dict[str, str], exception: dict[str, str], distribution: str) -> bool:
    expected_name = f"{exception['containingArtifact']}-{exception['containingVersion']}.jar"
    return (
        exception["distribution"] == distribution
        and exception["vulnerabilityId"] == finding["vulnerabilityId"]
        and exception["package"] == finding["package"]
        and exception["installedVersion"] == finding["installedVersion"]
        and exception["packagePath"] == finding["packagePath"]
        and Path(finding["packagePath"]).name == expected_name
    )


def evaluate(
    report: Path,
    distribution: str,
    accepted_report: Path,
    unapproved_report: Path,
    evaluation_date: date,
    policy_path: Path = POLICY,
) -> tuple[list[dict[str, str]], list[dict[str, str]]]:
    exceptions = load_policy(policy_path)
    expired = [
        exception
        for exception in exceptions
        if exception["distribution"] == distribution
        and date.fromisoformat(exception["expiresOn"]) < evaluation_date
    ]
    if expired:
        descriptions = ", ".join(
            f"{item['vulnerabilityId']} {item['package']} {item['installedVersion']} expired {item['expiresOn']}"
            for item in expired
        )
        raise ValueError(f"expired vulnerability exception(s): {descriptions}")

    all_findings = findings(load_json(report))
    accepted = [
        finding
        for finding in all_findings
        if any(matches(finding, exception, distribution) for exception in exceptions)
    ]
    unapproved = [finding for finding in all_findings if finding not in accepted]
    for output, content in ((accepted_report, accepted), (unapproved_report, unapproved)):
        output.parent.mkdir(parents=True, exist_ok=True)
        with output.open("w", encoding="utf-8", newline="\n") as destination:
            json.dump(content, destination, indent=2)
            destination.write("\n")
    return accepted, unapproved


def main(arguments: list[str]) -> int:
    if len(arguments) not in (4, 5):
        print(
            "usage: evaluate_container_vulnerabilities.py <raw-trivy.json> <distribution> "
            "<accepted.json> <unapproved.json> [yyyy-mm-dd]",
            file=sys.stderr,
        )
        return 2
    try:
        evaluated_on = date.fromisoformat(arguments[4]) if len(arguments) == 5 else date.today()
        accepted, unapproved = evaluate(
            Path(arguments[0]),
            arguments[1],
            Path(arguments[2]),
            Path(arguments[3]),
            evaluated_on,
        )
    except (OSError, ValueError, json.JSONDecodeError) as failure:
        print(f"Vulnerability exception evaluation failed: {failure}", file=sys.stderr)
        return 1
    if unapproved:
        print(
            f"Detected {len(unapproved)} unapproved HIGH/CRITICAL finding(s) in {arguments[1]}.",
            file=sys.stderr,
        )
        for finding in unapproved:
            print(
                f"{finding['vulnerabilityId']} {finding['package']} {finding['installedVersion']} "
                f"at {finding['packagePath']}",
                file=sys.stderr,
            )
        return 1
    print(
        f"Zero unapproved HIGH/CRITICAL findings in {arguments[1]}; "
        f"{len(accepted)} finding(s) accepted temporarily."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

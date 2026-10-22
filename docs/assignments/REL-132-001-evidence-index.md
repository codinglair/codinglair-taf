# REL-132-001 evidence index

This index distinguishes durable tracked records from ignored local outputs. Missing ignored
output in a later checkout is not silently treated as a pass; regenerate it with the named wrapper
or retain the original sanitized bundle before promotion.

| Area | Authoritative tracked record | Local/generated evidence | Recorded result |
| --- | --- | --- | --- |
| Dependency/security | `docs/assignments/handoffs/DEP-132-001-handoff.md`; DEP code/architecture reviews | `target/dep-132-001/`, `target/supply-chain/*-trivy.json` | PASS; 50 then 52 candidate surfaces, no HIGH/CRITICAL Jackson finding; two exact MCP HttpCore findings accepted separately |
| Serialization | `SER-132-001-handoff.md`; SER reviews; `docs/reference/jackson-dependency-remediation-1.3.2.md` | release-staging, consumer-smoke and SBOM outputs | PASS; Jackson-neutral APIs and bounded Jackson 3 migration |
| Session lifecycle | `RUN-132-001-handoff.md`; RUN-132-001 reviews | module/architecture and negative-fixture reports | PASS; `TAF-LIFECYCLE-001`, cleanup and isolation verified |
| Standalone scaffold | `SCF-132-001-handoff.md`; SCF reviews | boundary/component negatives | PASS; canonical containment and unchanged SUT verified |
| Runner conformance | `RUN-132-002-handoff.md`; RUN-132-002 reviews | conformance harness/Invoker reports | PASS; five exact negative fixtures, no sentinel execution |
| REST diagnostics | `REST-132-001-handoff.md`; REST reviews | REST integration and compatibility reports | PASS; bounded, redacted, correctly owned evidence |
| Evidence validator | `EVD-132-001-handoff.md`; final EVD reviews | validator mutation tests and `target/ver-132-001/evidence-validator.json` | PASS; final reviews supersede the handoff's stale re-review-pending header |
| Packaged consumers | `VER-132-001-handoff.md`; VER reviews | `target/ver-132-001/candidate-manifest.json`, consumer matrix, Trivy directory, dependency trees | PASS; 18 projects, 61 tests, 858 hashed candidate files |
| Public safety QE | `QE-132-001-handoff.md`; QE-132-001 reviews | `target/qe-132-001/acceptance-matrix.json`, summary and scenario directories | PASS; 26/26 |
| Report usability QE | `QE-132-002-handoff.md`; QE-132-002 reviews | `target/qe-132-002/summary.json`, logs, validators and reports | PASS locally; owner visual review open |
| Documentation | `DOC-132-001-handoff.md`; DOC reviews; documentation inventory | docs/API/schema Maven reports | PASS |

## Standalone Allure deliverables

The latest recorded QE run produced:

- `target/qe-132-002/testng-report/index.html`, recorded run SHA-256
  `6af1df3edda62e526125b7dbe71bdb69f3f8551a80a317a1ad1297c48b876742`;
- `target/qe-132-002/cucumber-report/index.html`, recorded run SHA-256
  `1bc9cbc1491a8ea0d3c664e1b6c4c4b7d43f62bbf804af38ae79252f2aa15d63`;
- `target/qe-132-002/testng-validator.json` and `cucumber-validator.json`, both valid with
  zero violations;
- `release/qe-132-002/readability-checklist.md`, the mandatory manual review procedure.

Allure UUIDs make HTML hashes run-specific. `summary.json` is authoritative for a regenerated
bundle. The reports are ignored outputs and are not present in every checkout; the owner must
retain or regenerate them before completing the manual checklist.

## Security and artifact provenance

- DEP recorded Trivy `0.72.0`, its database timestamps, raw report hashes, SBOM hash, image digest,
  and exact exception attribution.
- QE-132-001 matched 52 SBOM records to the candidate manifest and found zero HIGH/CRITICAL
  Jackson findings.
- QE-132-002 recorded candidate BOM SHA-256
  `1f73ea7b4d85289aebf6283f6376d8aadaaf0ae2f9fa9dde177206703bf34945`
  for its run; that older run identity is evidence provenance, not the hash of the current staged
  BOM listed in the readiness report.
- Current local artifact hashes in the readiness report were calculated from the prepared
  `target/staging-repository` at source SHA `c14754b...`.

## Regeneration commands

Use the repository wrapper and approved trust/mirror configuration. The principal deterministic
commands are:

```text
.\mvnw.cmd -B -ntp clean deploy -Prelease-staging
pwsh -NoProfile -File build-support/verification/ver-132-001.ps1 -SkipStage
pwsh -NoProfile -File build-support/verification/qe-132-001.ps1 -SkipCandidateRun
pwsh -NoProfile -File build-support/verification/qe-132-002.ps1 -SkipStage -AllureExecutable <approved-allure-executable>
java build-support/scripts/SyncDocVersion.java --check
```

Do not interpret a protocol fixture as live browser, device, or external-broker qualification.

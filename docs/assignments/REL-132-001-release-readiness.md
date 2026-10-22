# Codinglair TAF 1.3.2 engineering readiness

**Assessment date:** 2026-10-22

**Assignment:** REL-132-001

**Engineering verdict:** **READY WITH ACCEPTED RISK**

**Promotion verdict:** **NOT YET AUTHORIZED**

The reviewed candidate satisfies the deterministic engineering gates for the 1.3.2 corrective
release. The accepted, expiring MCP HttpCore vulnerability disposition remains visible, and the
release owner still must complete the separate checklist before promotion. This assessment does
not claim that GitHub Actions, manual report review, live infrastructure qualification, signing,
publication, image promotion, or a GitHub release has occurred.

## Candidate identity

| Identity | Assessed value |
| --- | --- |
| Source commit | `c14754b0ca40df447a13de4e4bf7f2de7d10b0e5` |
| Git tree | `771ab470024da28ae6b1e66dc94dc5b4661ba721` |
| Commit | `2026-10-22T09:07:04-05:00` — merge of reviewed DOC-132-001 |
| Initial worktree | Clean; no uncommitted candidate diff, so a diff digest is not applicable |
| Maven coordinates | `com.codinglair.taf:*:1.3.2`; root `revision` is authoritative |
| Toolchain checked | Oracle OpenJDK `25+36-3489`; Maven Wrapper `3.9.16`; Windows 11 |

The REL documents added by this assessment describe the candidate and do not change its Java,
Maven, workflow, schema, or runtime content. A later material candidate change invalidates the
affected evidence and requires reassessment.

## Gate reconciliation

| Gate | Verdict | Concrete evidence or precise limitation |
| --- | --- | --- |
| ADR-036 standalone boundary (CS-01–CS-07) | PASS | SCF-132-001 reviews PASS; VER packaged matrix passed 18 isolated projects and 61 tests; QE-132-001 passed all 26 scenarios, including traversal, symlink, SUT-root and four contamination negatives. |
| ADR-037 TestNG/session lifecycle (RN-01–RN-09) | PASS | RUN-132-001 and RUN-132-002 reviews PASS; provider/mixed-runner and lifecycle negatives fail before sentinels; TestNG/Cucumber cleanup and parallel isolation passed. The TestNG-only consumer rule is an approved breaking enforcement, not an accidental compatibility regression. |
| ADR-038 dependency/serialization (JK-01–JK-09) | PASS WITH ACCEPTED RISK | DEP/SER reviews PASS; published graphs converge; public APIs are Jackson-runtime neutral; 52 candidate SBOM surfaces had no HIGH/CRITICAL Jackson findings. Two visible MCP-image HttpCore HIGH findings have an exact owner-approved exception and expiry; see the genuine-defect record. |
| REST diagnostics (RE-01–RE-06) | PASS | REST reviews PASS; response/no-response classifications, bounded/redacted artifacts, stable ownership and pre-closure association passed affected-module and full compatibility verification. |
| Cross-capability evidence (EV-01–EV-06) | PASS | EVD final code and architecture reviews PASS after disposition of the initial findings; VER validator recorded 2 results/4 attachments/0 violations; QE-132-002 validators recorded 8 TestNG results/15 attachments and 4 Cucumber results/1 attachment, both with 0 violations. |
| Manual readability (EV-07) | OWNER ACTION OPEN | The standalone reports and mandatory checklist were produced and machine validated. Human visual review is deliberately not claimed. |
| Documentation/version governance (DC-01–DC-02) | PASS | DOC code and architecture reviews PASS; docs/API/schema gates passed. `SyncDocVersion --check` passes against the current root revision `1.3.2`; no mismatch is alleged from release planning metadata. |
| Readiness governance (RG-01) | PASS | This report separates engineering evidence, accepted risk, genuine gaps, and owner promotion actions without requiring CI, a final master SHA, or publication. |

Every 1.3.2 traceability row is covered by the grouped gates above. `JK-07` remains an explicitly
owned 1.3.3 follow-up and is not a 1.3.2 completion criterion.

## Candidate artifacts and hashes

The prepared checkout contains 858 staged repository files, 53 primary JARs, and 52 CycloneDX
JSON documents. These are local candidate artifacts, not published coordinates.

| Local artifact | SHA-256 |
| --- | --- |
| `codinglair-taf-1.3.2.pom` | `9647b919e6480c6738b19a465e9b06adc1c98520518aeb98bd7f0083fc96d331` |
| `codinglair-taf-bom-1.3.2.pom` | `b24a6f2be8c741065d5db2b382449fd951f926070f640ae05b4ee8d5d18a6c62` |
| `codinglair-taf-common-1.3.2.jar` | `c6520d68a755aca5abc976b1f987fc8ded43fbfe56a626909c5debb1c4825fa9` |
| `codinglair-taf-reporting-allure-1.3.2.jar` | `ea057b6af89fddb08363e1792a0a59e2812052ce8d9509d1c2fd8d1121ac5c48` |
| `taf-mcp-transport-http-1.3.2.jar` | `1f498e6bcf5423b368305f20ea5cbc0674b838e7b9dfef0d2346173dbc7568ba` |

The detailed evidence locations, provenance, and run-specific report hashes are indexed in
`REL-132-001-evidence-index.md`.

## Compatibility and security disposition

No REL-owned public API, schema, coordinate, configuration key, dependency, lifecycle behavior, or
serialized format changed. The candidate intentionally enforces standalone automation, TestNG
consumer ownership, and active invocation-bound sessions. Boot 3 and Boot 4 SUT compatibility is
process/protocol based; TAF does not share a mapper or application context with the SUT.

No secret value is recorded here. Synthetic redaction canaries remain test data and the exact
historical literal is narrowly allowlisted for Gitleaks; current-tree and history scans passed in
QE-132-002. Controller evidence continues through `ArtifactCollector`.

## Final disposition

Engineering readiness is complete and reproducible from the indexed local evidence. Promotion is
not authorized by this document. The release owner must complete and sign
`REL-132-001-owner-release-checklist.md`; any failed owner gate or material candidate change
reopens the corresponding assessment row.

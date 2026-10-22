# REL-132-001 genuine defects and accepted risks

## Unresolved engineering defects

**None identified in the assessed 1.3.2 candidate.**

All corrective assignment code reviews are PASS, and all required architectural reviews are PASS
or explicitly not required. EVD-132-001's handoff header still says re-review pending, but its
later code review and architectural review both issue final PASS verdicts. QE-132-002's initially
reported findings were repaired or dispositioned by its final PASS reviews. These stale/intermediate
labels do not conceal an open defect.

## Accepted release risk

The MCP STDIO image contains shaded Apache HttpCore 5.3.6 findings `CVE-2026-54399` and
`CVE-2026-54428` through `docker-java-transport-zerodep:3.7.1`. They are genuine HIGH findings,
not suppressed or reclassified. The owner-approved evaluator accepts only the recorded CVE,
package/version, artifact/version, packaged path, distribution, and validity window; unexpected,
drifted, or expired findings fail closed.

- Reassess by: **2026-11-18**.
- Remove no later than: **2026-12-18 or the 1.3.3 release gate**, whichever controls.
- Current release disposition: accepted risk; zero unapproved HIGH/CRITICAL image findings.

Expiry, scope drift, a new unapproved finding, or failure of the replacement transport's Docker,
LocalStack, concurrency, or cleanup tests becomes an engineering blocker.

## Required local verification gaps

None for REL-132-001. Required metadata, hashes, documentation synchronization, review
dispositions, and existing deterministic evidence were checked. The prior Linux-only control-plane
tmpfs assertion and earlier Maven TLS/sandbox failures are environment qualifications already
classified in their handoffs; they are not hidden product passes.

## Owner-managed actions, not defects

GitHub Actions, manual Allure readability, full browser/device/external-broker lanes, the Linux
control-plane smoke, final provenance binding, signing, tagging, Central publication, Docker image
promotion, and release creation remain open. `NOT RUN` for these items is not an implementation
defect, but promotion must not proceed until the owner applies the release policy and records the
required outcomes.

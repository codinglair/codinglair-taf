# REL-132-001 owner release and promotion checklist

This checklist is separate from engineering completion. The release owner records evidence and
authorizes any consequential operation; this document does not grant that authorization.

## Candidate freeze and hosted gates

- [ ] Confirm the promotion candidate still includes source SHA `c14754b0ca40df447a13de4e4bf7f2de7d10b0e5`,
  or record the replacement SHA and reassess every affected gate.
- [ ] Confirm the final worktree/diff identity and bind staged Maven artifacts, SBOMs, provenance,
  and container images to it.
- [ ] Run or review the required GitHub Actions gates; record run URLs, conclusions, artifact IDs,
  hashes, retention, and any rerun rationale.
- [ ] Run the Linux control-plane image smoke and required release security/signing/provenance
  checks; verify zero unapproved HIGH/CRITICAL findings.
- [ ] Confirm the HttpCore exception is still in exact scope and unexpired; record explicit risk
  acceptance and the remediation owner/deadline.

## Manual Allure readability

- [ ] Retain or regenerate both single-file reports and their matching raw results, expectation
  matrices, validator JSON, candidate identity, and hashes.
- [ ] Complete every item in `release/qe-132-002/readability-checklist.md` against the TestNG report.
- [ ] Complete every item in the same checklist against the Cucumber report.
- [ ] Record reviewer, timestamp, report hashes, PASS/FAIL, and any defect reference. Machine
  validation does not substitute for this review.

## Live qualification

- [ ] Run the release-policy browser matrix on approved infrastructure.
- [ ] Run required Android and Apple device/simulator lanes and record the exact tuples.
- [ ] Run required external Kafka, RabbitMQ, and JMS lanes.
- [ ] Record any unavailable lane as an explicit owner disposition; do not relabel a local fixture
  as live qualification.

## Promotion

- [ ] Confirm changelog, migration guide, compatibility notes, and root/current documentation
  markers describe the exact promoted revision.
- [ ] Obtain required human approvals for merge, tag, sign, publish, image promotion, and release.
- [ ] Verify Maven Central and Docker coordinates/digests after publication and retain the final
  supply-chain evidence.
- [ ] Mark the release promoted only after all mandatory policy checks pass. If a check fails or the
  candidate changes materially, stop promotion and reopen the affected engineering gate.

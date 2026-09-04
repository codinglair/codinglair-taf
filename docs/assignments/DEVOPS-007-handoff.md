# DEVOPS-007 Handoff: Gitleaks PR Secret-Scanning Gate

## Status

**Partially complete.** The repository implementation and local full-history
baseline are complete. GitHub-hosted clean-PR and controlled-failure PR evidence
is still required before the assignment can be marked complete.

## Scope delivered

- Added a dedicated pull-request/reusable Gitleaks workflow for `master`,
  `staging`, `release-*`, and `release/**`.
- Made its reusable-workflow result an unconditional input to the stable
  `PR gate`; failure, cancellation, or an unexpected skip fails closed.
- Added one repository-owned scan command shared by GitHub, Jenkins, GitLab,
  and contributors.
- Added structural regression coverage, contributor guidance, and three narrow
  allowlist entries for demonstrably non-secret historical text.

## Files changed

- `.github/workflows/secret-scanning.yml`
- `.github/workflows/pull-request.yml`
- `.gitleaks.toml`
- `build-support/scripts/run-gitleaks.sh`
- `build-support/ci/GitleaksContractTest.java`
- `build-support/ci/PrGate.java`
- `build-support/ci/PrGateTest.java`
- `build-support/ci/WorkflowContractTest.java`
- `build-support/ci/ReferencePipelineContractTest.java`
- `Jenkinsfile`
- `.gitlab-ci.yml`
- `CONTRIBUTING.md`
- `docs/assignments/DEVOPS-007-handoff.md`

The pre-existing `.gitignore` modification was preserved and is not part of
DEVOPS-007.

## Image, license, and scan contract

The implementation uses the official MIT-licensed GitHub Container Registry
image `ghcr.io/gitleaks/gitleaks:v8.30.1` pinned to manifest digest
`sha256:c00b6bd0aeb3071cbcb79009cb16a60dd9e0a7c60e2be9ab65d25e6bc8abbb7f`.
Upstream package and license evidence:

- https://github.com/gitleaks/gitleaks/pkgs/container/gitleaks
- https://github.com/gitleaks/gitleaks/blob/v8.30.1/LICENSE

CI resolves the GitHub event base and head commit objects, rejects a missing or
empty range, and scans `BASE_SHA..HEAD_SHA`. This covers every commit reachable
from the PR head but not the base branch, including content added and deleted in
different PR commits. Checkout uses complete history and disables persisted
checkout credentials.

The container receives no secrets or token, has networking disabled, mounts the
checkout read-only, runs without privilege, uses 100-percent redaction, and does
not create or upload a findings report. Image pull failure, invalid configuration,
unresolved range, findings, or scanner failure returns nonzero.

## Allowlist review

`.gitleaks.toml` extends maintained defaults and contains exactly three entries:

1. An exact image-policy test-fixture filename that contains an Android API
   level label.
2. An exact published Android SDK archive SHA-256 integrity checksum.
3. An exact documentation sentence fragment containing a secret-reference URI
   and failed outcome, not a resolved value.

Each entry is adjacent to its rationale and matches exact text. No path, file
type, entropy, rule, directory, or commit is broadly excluded. Default rules
remain active; the local scan still fails for non-allowlisted synthetic secrets.

## Verification evidence

- `javac ... build-support/ci/{PrGate,PrGateTest,WorkflowContractTest,GitleaksContractTest,ReferencePipelineContractTest}.java` — PASS on Java 25.
- `java -cp target/ci-support PrGateTest` — PASS, eight gate scenarios including
  failed/skipped secret scanning.
- `java -cp target/ci-support GitleaksContractTest` — PASS.
- `java -cp target/ci-support WorkflowContractTest` — PASS.
- `java -cp target/ci-support ReferencePipelineContractTest` — PASS.
- `./mvnw.cmd -B -ntp -DskipTests -pl codinglair-taf-runtime/taf-test-definitions -am test-compile dependency:build-classpath -Dmdep.outputFile=target/ci-classpath.txt`
  — initial sandbox attempt failed on Maven-cache access; approved rerun PASS.
- Full-history Docker scan before narrow exclusions — expected FAIL with three
  redacted false-positive candidates; no value was copied into this handoff.
- Full-history Docker scan after exclusions — PASS, two commits and about 3.02 MB
  scanned, no leaks found.
- `./mvnw.cmd -Pdocs verify` — initial sandbox attempt failed on Maven-cache
  access; approved rerun PASS across all 41 reactor projects on Java 25.

Full-history baseline: repository
`git@github.com:codinglair/codinglair-taf.git`, branch `ci-DEVOPS-007`, commit
`1806ff244cc03fb2a8b824c3dbc1de64a7cd0886`. Exact scanner invocation is the
Docker command encoded by `sh build-support/scripts/run-gitleaks.sh history`.
It remained strictly inside this migrated repository and did not import or scan
former-repository history.

## Acceptance status

- PASS — dedicated workflow and protected target branches.
- PASS — official immutable Docker image; no action wrapper.
- PASS — complete PR commit range, checked-out content, and hidden paths.
- PASS — fail-closed range/configuration/scanner/gate structure.
- PASS — least privilege, no passed repository secrets, redacted output, and no
  retained raw report.
- PASS — narrow documented allowlist and maintained default rules.
- PASS — version-matched contributor command and safe remediation guidance.
- PASS — Jenkins and GitLab invoke the same repository-owned history scan.
- PASS — local clean full-history baseline at the recorded repository/branch/SHA.
- PASS — no excluded former history was imported or inspected.
- PENDING — GitHub Actions clean-PR and controlled synthetic-secret PR evidence.
- PENDING — GitHub-hosted proof that the aggregate `PR gate` blocks on the
  controlled scan failure.

## Residual risks and next actions

Open a controlled test PR containing only a generated synthetic value, record
the failing Gitleaks and aggregate gate run links, remove the fixture, and record
a subsequent clean passing run. Never put the fixture value or raw finding in
the handoff, PR discussion, logs, screenshots, or artifacts. Once those links
are recorded, DEVOPS-007 can be marked complete.

No real credential was used, reproduced, retained, or copied into this evidence.

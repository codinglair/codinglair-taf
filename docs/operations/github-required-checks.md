# GitHub pull-request required check

The primary workflow is `.github/workflows/pull-request.yml`. Configure branch
rulesets for pull-request base branches matching `release-*` and `release/**` to
require exactly this repository-owned check:

- `PR gate`

Confirm the exact check-run name from the representative GitHub pull-request run before applying the rule. An authorized repository administrator must configure the ruleset; the workflow does not modify repository settings.

## Gate behavior

`Unit tests (Java 25)` is unconditional and runs the reactor on every pull request. `Change impact` is also unconditional and tests its fixtures before classifying the complete base-to-head Git diff. Added, modified, deleted, copied, and both sides of renamed paths participate in selection. An invalid comparison fails closed.

The following jobs are conditional:

- `Affected integration and contract tests`
- `Cross-module smoke`
- `Browser smoke`
- `Android emulator Appium smoke`

Conditional jobs are not independently required because a legitimate path exclusion produces a skipped check. `PR gate` always runs after every lane and accepts `skipped` only when the tested impact decision excluded that lane. It rejects failures, cancellations, unknown results, and selected lanes that skipped.

## Mobile runner requirement

Mobile, Android qualification recipe, shared Runtime/mobile dependency, root
build, workflow, or CI-support changes select the Appium lane. It uses the
explicit `ubuntu-24.04` x86-64 hosted runner and builds the repository-owned
`containers/android-emulator/google/Dockerfile` recipe ephemerally. The runner
must provide Docker, Compose, PowerShell, and readable/writable `/dev/kvm`
access. The job may grant the unprivileged runner user a narrow ACL on the KVM
device when the hosted image requires it; it does not enable privileged
containers, install host Android tooling, or use credentials.

The lane verifies approval/evidence references, builds without importing a
Codinglair image or publishing image state, and starts separate emulator and
pinned Appium services from `compose.qualify.yaml`. It runs the Maven Wrapper
with `-Pandroid-emulator` and parses Surefire XML to prove
`AndroidEmulatorSmokeTest` executed with zero skips, failures, or errors. It
captures bounded diagnostics on failure and always tears down the Compose
project. Missing KVM, unhealthy services, connection failure, a skipped smoke,
and incomplete cleanup all fail the lane and therefore `PR gate`.

MOB-003 run `90050184653` already proves hosted-runner KVM, ephemeral build,
healthy emulator/Appium services, zero-skip smoke, cleanup, and aggregate-gate
success. The resulting image identity binds that run's evidence only; it is not
a distributable or consumer image contract.

## Evidence required before closure

Accepted MOB-003 evidence supplies the mobile execution proof. DEVOPS-001R
closure additionally records:

- the commit SHA and immutable passing workflow-run URL;
- Java 25 and `ubuntu-24.04` runner image evidence;
- unconditional reactor execution and correct conditional selection;
- successful KVM preflight, Compose health, non-skipped emulator smoke, and teardown;
- the configured branch ruleset requiring the confirmed `PR gate` check name, or an explicit pending-administrator status.

Deterministic repository fixtures prove selected-lane failure/skip rejection and
failure-only artifact/cleanup structure. A new deliberate failing remote commit
is necessary only if those contracts or the accepted evidence no longer cover
the behavior; never merge a deliberate failure.

Failure artifacts are retained for seven days and contain only bounded test reports and lane diagnostics. Workflow permissions are limited to repository-content read access, no secrets are consumed, and superseded runs for the same pull request are cancelled.

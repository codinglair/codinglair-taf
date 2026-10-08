# Local verification

From any directory, run the script with PowerShell 7 using its path:

```powershell
pwsh -NoProfile -File .\build-support\local\local-verify.ps1
```

It runs all ten commands from the supplied local verification request, including
the trailing offline Appium architecture/dependency check. Each check prints its
command, summary/output, errors and PASS/FAIL result. Maven reactor size and test
counts are discovered from output. Consumer smoke uses its nonrecursive Invoker
summary and requires positive passes with zero failures, errors or skips.
Maven reactor checks require exit code zero, every reactor entry marked SUCCESS,
and BUILD SUCCESS. Printed error diagnostics remain visible but do not change
the result, including ignored Javadoc link errors emitted by tests.
Exit code 0 requires all checks to pass; exit code 1 means at least one failed.
Failures do not prevent later checks from running. Maven output shows only the
final reactor (or consumer Build Summary) and build footer, plus errors. Android
also shows its final test results, BDD its final test count, release staging its
deployment-skip lines, and the offline check its dependency mismatch result.
Nested consumer Maven logs and ordinary plugin/test logs are suppressed.

Prerequisites: Java 25, Docker with Compose, the existing qualified API 34 image
and KVM support, installed Playwright browsers, Allure on PATH, and
`SAUCE_DEMO_PASSWORD` already supplied through the process environment. The
script sets the approved secret reference without printing or supplying a password.
Use existing Maven settings/mirrors; the script adds no dependencies or configuration.

Release staging deploys to `target/staging-repository` as configured in the
parent POM. Android uses a unique Compose project name, checks both containers'
health, always attempts teardown, and confirms no project containers or networks
remain. The Appium port remains 4724, so avoid concurrent runs. Environment
overrides and the caller's directory are restored. Hard process termination can
prevent cleanup; remove the printed Compose project manually in that case.

The checks run real clean/install/deploy builds and container/browser suites.
This script covers the supplied request; the CI release verification matrix
remains authoritative for release qualification.

Run the dependency-free script tests separately:

```powershell
pwsh -NoProfile -File .\build-support\local\local-verify.Tests.ps1
```

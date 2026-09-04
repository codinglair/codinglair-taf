# Single-file Allure report publishing

The optional Allure adapter publisher packages finalized, sanitized Allure results into one HTML
file that opens offline. Ordinary `allure-results` files remain unchanged and can still be used by
Allure directory reports, CI plugins, and reporting portals.

## Configuration

```yaml
taf:
  reporting:
    allure:
      single-file:
        enabled: true
        results-directory: target/allure-results
        output-directory: target/taf-reports
        report-name: SauceDemo Functional Tests
        timestamp-pattern: yyyyMMddHHmm
        executable: allure
        timeout: 2m
```

Defaults are disabled publication, `target/allure-results`, `target/taf-reports`, report name
`TAF Test Report`, timestamp pattern `yyyyMMddHHmm`, executable `allure`, and a two-minute timeout.
Invalid names, traversal characters, unsafe timestamp output, missing results, generator failures,
timeouts, and invalid HTML fail with an actionable publication stage.

The example produces and logs this resolved artifact path:

```text
target/taf-reports/202608092145/SauceDemo Functional Tests_202608092145.html
```

If that minute is already reserved, the next directory is `202608092145-2`; the existing report is
never overwritten. Publication is once per TestNG execution. The approved Cucumber runner uses the
TestNG adapter, so Cucumber and TestNG-orchestrated Cucumber share the same terminal callback.

## Local and container execution

Local and other non-container environments may configure an approved Allure 2 CLI path. Managed CI
and Kubernetes jobs use the runner image built from `containers/taf-test-runner/Dockerfile`. It pins
Java 25 by base-image digest, verifies the Allure 2.36.0 archive SHA-256, runs as UID/GID `10001`,
and sets `TAF_TEST_ALLURE_EXECUTABLE=/usr/local/bin/allure` for real-generator verification.

```shell
docker build --tag codinglair/taf-test-runner:java25-allure2.36.0 containers/taf-test-runner
docker run --rm codinglair/taf-test-runner:java25-allure2.36.0 allure --version
docker run --rm codinglair/taf-test-runner:java25-allure2.36.0 java -version
```

Mount the complete job workspace at `/workspace` with ownership or an `fsGroup` permitting UID
10001 to write results and reports. A Kubernetes Job container fragment is:

```yaml
securityContext:
  runAsNonRoot: true
  runAsUser: 10001
  runAsGroup: 10001
  fsGroup: 10001
containers:
  - name: tests
    image: codinglair/taf-test-runner:java25-allure2.36.0
    workingDir: /workspace
    command: ["./mvnw", "verify"]
    volumeMounts:
      - name: job-workspace
        mountPath: /workspace
volumes:
  - name: job-workspace
    emptyDir: {}
```

The HTML is written below the mounted workspace, not the container layer. CI retrieves it before
the Job and its `emptyDir` are removed.

## Native CI artifact archiving

Archive `target/taf-reports/**/*.html` (or the configured path) with the CI system's native artifact
feature. GitHub Actions should use `actions/upload-artifact` after testing with `if: always()` and
`if-no-files-found: error`. Jenkins uses `archiveArtifacts`; GitLab declares the path under
`artifacts.paths`. Downstream steps must not read or log embedded HTML contents.

Object-store upload, PVC retention, email delivery, portal hosting, and report cleanup are outside
REP-005. The HTML can contain approved screenshots, request/response evidence, or other attachments;
apply the project's evidence-retention and distribution policy before sharing it outside the CI or
reporting portal trust boundary.

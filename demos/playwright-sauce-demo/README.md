# SauceDemo Playwright golden project

This is the focused Spring Boot Playwright/POM/PCOM consumer reference. Runtime owns preflight,
`TestSession`, reporting, failure evidence, and cleanup. Consumer code owns pages, the reusable
header component, workflows, typed models, validators, and test intent.

The golden project enables REP-005 single-file Allure publishing with report name
`SauceDemo Functional Tests`. Install the approved Allure 2 CLI locally or set
`TAF_ALLURE_EXECUTABLE`; managed CI and Kubernetes jobs use the pinned non-root runner image in
[`docs/reporting/single-file-allure.md`](../../docs/reporting/single-file-allure.md). Completed runs
write one HTML file below `target/taf-reports/<yyyyMMddHHmm>/` while preserving
`target/allure-results`. Email delivery is outside REP-005.

## Configuration

`application.yaml` is authoritative. `application-local.yaml` changes only local browser behavior;
`application-ci.yaml` changes only CI behavior. Supply these values through the execution
environment:

- `SAUCE_DEMO_BASE_URL`
- `SAUCE_DEMO_PASSWORD_REF` (for example `secret://env/SAUCE_DEMO_PASSWORD`)
- `TAF_JASYPT_MASTER_KEY_ENV` naming the externally supplied key variable for Jasypt references
- the environment variables selected by those references
- optional `TAF_REPORT_OUTPUT`, `SAUCE_DEMO_ACTION_TIMEOUT`, and `TEST_ENVIRONMENT`
- optional `TAF_ALLURE_RESULTS`; when absent, the single-file publisher follows the standard
  `allure.results.directory` system property and otherwise defaults to `target/allure-results`

Never put resolved credentials in YAML, CSV, command lines, logs, reports, screenshots, or
artifacts. CSV stores only opaque provider references. Its three checked-in Jasypt payload placeholders
must be replaced locally with independently generated ciphertext before a live run; the key remains
external. Environment references remain supported as the simpler alternative.

See [`taf-secrets-local`](../../codinglair-taf-runtime/taf-secrets-local/README.md) for the safe CSV
generator, CI binding, migration, troubleshooting, and rotation. Jasypt is a local/air-gapped option,
not the preferred external secret store. Consumers can implement another provider through the
`taf-secrets-api` boundary.

## Data and suites

Paired definitions are under `src/test/resources/test-data`. `TC0001` is the explicit login-subject
test, `TC0002` deliberately demonstrates a product-description failure with login as a prerequisite
workflow, and `TC0003` drives the
Cucumber purchase scenario. TestNG and Cucumber remain separate:

```powershell
.\mvnw.cmd test
.\mvnw.cmd -Pfunctional-suite test
.\mvnw.cmd -Pbdd-suite test
.\mvnw.cmd -Pbrowser-smoke verify
```

`TC0002` validates SauceDemo's displayed backpack description exactly. Functional and browser-smoke
runs are release gates and are expected to pass after explicit PWD-002 secret normalization.

The default command is deterministic and does not open a browser. Live profiles require authorized
environment values and access to SauceDemo.

## Reporting

Consumer code uses only TAF-neutral annotations and APIs. Expected Allure hierarchy is test/scenario,
business workflow or BDD step, page/component action, controller diagnostic, then validation.
Each level appears once; passwords and other sensitive values are redacted before adapter delivery.
Inspect serialized output under the configured report directory (default
`target/allure-results`).

# Consumer evidence validation

`AllureEvidenceValidator` is the Allure-format adapter boundary for deterministic release evidence
checks. It validates real `*-result.json` and `*-container.json` files, recursively inspects nested
steps, resolves each attachment under the supplied results directory, rejects duplicate references
and unreferenced files, and compares result names, statuses, attempt counts, attachment ownership,
content types, redaction canaries, and standalone-report markers with a supplied JSON expectation
matrix.

Run it from the repository root after the consumer has produced raw results and an Allure 2
`--single-file` report:

```powershell
pwsh -NoProfile -File build-support/verification/verify-consumer-evidence.ps1 `
  -Results <results-dir> -Report <standalone-html> -Matrix <expectations.json>
```

The command writes `target/consumer-evidence/validator.json` unless `-Output` selects another path.
Exit `0` means the complete expectation set passed, `1` means validation violations were written,
and `2` means the CLI arguments were invalid. A missing input is a wrapper failure and is never
treated as unavailable infrastructure.

## Expectation contract

The JSON object supports `expectedResultCount`, `expectedResults`, `forbiddenPatterns`, and
`standaloneRequiredMarkers`. Each expected result declares
`name`, exact `status`, exact `count`, and attachments with `name`, `count`, `contentType`, and
`ownerStep`; `omitted: true` asserts an intentional omission. Repeat a result expectation with the
same name and different status to describe rerun attempts. Expected failures must retain status
`failed` or `broken`; callers must not ignore the producer's Maven exit globally.

`requiredFields` names fields inside textual attachment payloads, not fields in Allure result JSON.
The validator checks standalone attachment embedding unconditionally. Diagnostic fields are required
only for failure or diagnostic evidence whose capability expectation declares them; they are not a
universal requirement for every attachment type.

Visual evidence is valid only when its capability row says visual publication is explicitly
permitted. Otherwise the matrix must require omission. Text redaction does not authorize pixels.
Raw results, the standalone HTML, the exact expectation matrix, and validator JSON are one evidence
set and must be retained together even when validation fails.

## Manual release review

- Open the retained single-file report offline and navigate to every expected success and failure.
- Open each attachment from its owning nested step; do not accept an HTML file merely because it is
  nonempty.
- Confirm failed attempts remain visibly failed and reruns are distinct.
- Confirm operation, session, test, step, attempt, diagnostic, content-type, and omission fields are
  readable and contain no secret canary or unsafe visual content.
- Match the report, raw results, validator JSON, matrix, candidate revision, and SHA-256 manifest.

CI must upload this complete sanitized set with `if: always()` and fail when any required member is
missing. `VER-132-001` owns invoking the validator from packaged-consumer pipelines; this validator
contract is the authoritative input/output seam for that wiring.

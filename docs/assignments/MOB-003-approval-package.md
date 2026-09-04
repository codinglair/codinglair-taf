# MOB-003 Candidate A Approval Package

## Decision and post-approval evidence

The governed deliverable is a reference qualification recipe and gate. Its
ephemeral local/CI image is a framework-development health checkpoint;
consumers provide their own mobile infrastructure. No image publication or
consumer delivery is permitted. DEVOPS-001R integrates the qualification
recipe and gate—not a distributable image digest.

Product Owner governance approval was recorded as `MOB-003-GOV-001` in
`docs/decisions/MOB-003-reference-qualification-approval.md` for the license
dispositions and restricted reference qualification recipe with ephemeral
local/CI construction. This approval does not publish, transfer, deliver,
adopt, or authorize distribution of the image.

This records an operating-policy decision, not legal advice or legal approval.
Only call it legal review if an authorized legal reviewer actually performs and
records one with scope, date, reviewer identity/role, and decision reference.

Proposed operating model: keep the checksum-pinned reference qualification
recipe in the repository and construct an ephemeral image only in an authorized
local or CI execution after explicit Android SDK license acceptance. The image
is a framework-development health checkpoint. Consumers provide their own
mobile infrastructure; no generated image or image digest is published,
transferred, or delivered to them.

## Exact candidate

- Candidate: Android 14/API 34 Google APIs x86_64 revision 14.
- Platform: `linux/amd64`.
- Exact local OCI digest:
  `sha256:a60cc06baa93451336aa97b36540e5136b77be72019be76dcf8da7df53b4cebf`.
- Builder base:
  `sha256:781449467ffb6f04218f09b1ecdcdc7d22b289ee5da9ec498b024e24ad7a6db7`.
- Runtime base:
  `sha256:1e0a86e57d247923571b75e0aaf48a1449cf8c543d51fb3e07a4a7d7bfa79316`.
- No image was pushed, published, transferred, delivered to consumers, or
  adopted by production Compose. DEVOPS-001R is authorized, after this gate
  passes, to integrate only the qualification recipe and gate—not a
  distributable image digest.

## Completed technical evidence

Evidence is under ignored `target/mob-003/`; integrity is recorded in
`target/mob-003/evidence-manifest.sha256`.

| Evidence | Result |
|---|---|
| SPDX SBOM | Valid SPDX 2.3; 253 packages; SHA-256 `974abe11d104b4c627f56a7cc6824d9afdb5a501ab7fef6b1434cfc0e6b74fa0` |
| Provenance | BuildKit max-mode provenance verified; seven immutable materials; SHA-256 `7e3e7b7f2c294b5181d0de8b2e03c6038cacbd937d8090356d71ca31e6e8e49e` |
| Runtime scan | Exact candidate digest; 32 findings; every finding classified `CONTROL_PLANE` with SARIF path evidence |
| Builder scan | Exact pinned Python builder digest; 128 findings; every finding classified `BUILDER` with SARIF path evidence; builder content absent from runtime; outputs tied to checksum-verified inputs, provenance, and exact final digest |
| Scanner | Docker Scout 1.24.0, commit `b1c9331b2166aef7ec690aa16fd655b8798ea4c6`, scan date 2026-08-27 |
| Scanner database | Docker Scout service-managed advisory database; CLI 1.24.0 exposes no immutable database revision, which is explicitly recorded rather than fabricated |
| Notices | Emulator, platform-tools, and system-image notices exported into `candidate-a-NOTICE.bundle.txt` |
| SDK acceptance | Explicit `build.ps1 -AcceptAndroidSdkLicense` and BuildKit argument recorded in `android-sdk-license-acceptance.txt` |
| License inventory | 256 machine-readable records supporting the summarized obligation families and three Google exceptions |
| License report | `MOB-003-license-obligation-report.md`; 38 original `NOASSERTION` rows resolved, zero remain |
| Resolution proof | `candidate-a-noassertion-resolutions.csv`; all 38 original SPDX IDs, mappings, obligations, and evidence locators; checksum in evidence manifest |
| SEC-003 evidence | Complete Candidate A properties contain 160 findings and 256 license records |
| SEC-003 result | `REJECT` only for dispositions pending the summarized approval; no technical rejection reason and no `UNKNOWN` finding plane |
| Local runtime | Two clean real Appium smoke runs passed with one candidate smoke execution and zero skips per run |
| Failure cleanup | Controlled failure passed; no project container or network remained |

The original 38 `NOASSERTION` records are resolved in the license report: one
synthetic image aggregate was removed, 32 Ubuntu records map to 13 exact-image
copyright families, and five Android guest records map to authoritative
upstream licenses. The corrected inventory also makes the emulator,
platform-tools, and system image explicit Google SDK exceptions.

## Approval application and result

The approval covers the report's three summarized decisions: the
no-image-publication build model, controls for the three Google SDK artifacts,
and grouped open-source compliance controls. Package-by-package approval was
not requested.

The following was executed:

```powershell
containers/android-emulator/google/complete-evidence.ps1 `
  -ApprovalReference 'MOB-003-GOV-001' `
  -ApprovedLicenseReportSha256 `
    '105a5a361747e3de68457ae14ae63506bb84b4ad42d9316c98dea8d00a962164'
```

Result: PASS. The script verified the approved report checksum, mapped the
approval to all 256 machine-readable rows, recorded the reference against the
exact digest, and SEC-003 independently exited 0 with JSON `result=PASS` and no
reasons.

Post-approval hashes:

- approved inventory: `bb9eafb5fe22bffb5ba79d2b27277f3378d569e0a27c9e4cf76b64be93f06e04`;
- SEC-003 properties: `5d31adefd5538aa5ed32116f67dc549e53013b139123a15cab1252acd0f3d313`;
- SEC-003 result: `aed8698e61a0e9fca2157f3fcbf032a7511bd2d093657f6f6c5406208cf8f934`;
- approval record: `5156ef26bdf68264aa42f1c7c5ff23e5feedbd3637442b14d27570c3dcbcdea3`;
- post-approval evidence manifest:
  `56a4e01f7f5e468c536407016e3008dfa0abce1dcb21e4d9d262cd80755d3f7f`.

The remaining post-approval gate is the manual GitHub qualification. It was not
dispatched because the workflow remains untracked locally and is absent from
`origin/feature-MOB-003`; GitHub therefore has no workflow to execute. A human
reviewed commit/push is required before dispatch. In every status, the image
must not be published, transferred, delivered to consumers, or adopted.
DEVOPS-001R may integrate the reference qualification recipe and gate only
after the GitHub gate passes; it must not consume or expose a distributable
image digest.

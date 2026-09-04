# MOB-003 Handoff: Controlled Google Android Emulator Image Qualification

## 1. Status

**MOB-003: COMPLETE — GITHUB QUALIFICATION PASSED**

MOB-003 delivers a reference qualification recipe. Its image is ephemeral
local/CI state used as a framework-development health checkpoint. Consumers
provide their own mobile infrastructure through the Appium contract. No image
publication or consumer delivery is permitted. DEVOPS-001R integrates the
qualification recipe and gate—not a distributable image digest.

Candidate A implementation, supply-chain evidence, policy evaluation, local
runtime qualification, cleanup verification, CI contracts, and required GitHub
PR qualification are complete. Product Owner governance approval is recorded in
`docs/decisions/MOB-003-reference-qualification-approval.md` as
`MOB-003-GOV-001`. GitHub run `90050184653` completed the required PR Appium
lane and aggregate PR Gate successfully for PR `#74`.

SEC-003 itself is complete and approved. Candidate A's approved evidence now
exits `0` with JSON `result=PASS`, no rejection reasons, and all 256 reviewed
license records mapped to `ALLOWED` under `MOB-003-GOV-001`.

Candidate A was not pushed, published, transferred, delivered to consumers, or
adopted by production Compose. DEVOPS-001R may eventually integrate only the
qualification recipe and gate—not a distributable image digest.

## 2. Candidate disposition

| Candidate | Technical result | Disposition |
|---|---|---|
| A — Android 14/API 34 Google APIs x86_64 r14 | Build, immutable-input checks, KVM boot, API identity, separate Appium, local and GitHub zero-skip smokes, topology, cleanup, governance approval, SEC-003 pass, and aggregate GitHub PR Gate pass | **Qualified and complete for the restricted reference-qualification model** |
| B — Android 11/API 30 | Not evaluated | Correctly deferred because Candidate A has no essential technical failure |

No third candidate was evaluated.

## 3. Exact artifact and inputs

- Candidate linux/amd64 OCI digest:
  `sha256:a60cc06baa93451336aa97b36540e5136b77be72019be76dcf8da7df53b4cebf`.
- Google container scripts commit:
  `0654f694b46794fae4b178f1e1a17cb60c5d2d34`.
- Emulator 37.1.11/build 15917651 SHA-256:
  `95771e0ae431897b2a4bd2d97fa095f29a8b0624a7b216baf529f9306161c266`.
- Android 14/API 34 Google APIs x86_64 r14 SHA-256:
  `783a40134baf4f3012d4464fbe1571b1612a0dbd2e7a44d14bd8328923443833`.
- Platform tools 37.0.1 SHA-256:
  `d230f13842f60f782a8645f9c813f8f845bf36089ea7289f28c48f17979313f1`.
- Python builder linux/amd64 digest:
  `sha256:781449467ffb6f04218f09b1ecdcdc7d22b289ee5da9ec498b024e24ad7a6db7`.
- Ubuntu runtime linux/amd64 digest:
  `sha256:1e0a86e57d247923571b75e0aaf48a1449cf8c543d51fb3e07a4a7d7bfa79316`.
- Ubuntu snapshot: `20260810T000000Z`.
- SBOM generator linux/amd64 digest:
  `sha256:187e1892a7752c9384c59aba9517dd8e40610b748c72773e87b63720514463c2`.

The final digest identifies one ephemeral local qualification image and binds
its evidence. It is not an approved or distributable image digest.

## 4. Implemented behavior and contracts

- The checksum-enforced multi-stage Dockerfile keeps Python and downloaded
  archives out of the runtime.
- Runtime UID/GID is 10001, metrics are disabled, data is ephemeral, ADB is not
  published to the host, `/dev/kvm` is the only mapped device, and privileged
  mode is prohibited.
- `build.ps1 -Clean` is the committed clean-build entry point and records
  max-mode provenance plus an SPDX attestation.
- `complete-evidence.ps1` validates the SBOM and provenance, produces the exact
  license/finding inventory and checksums, generates Candidate A SEC-003
  properties, runs the policy, and asserts that only unapproved license
  dispositions reject.
- The 256-row inventory is `1` project record plus `252` actual SPDX components
  plus `3` explicit Google artifact records. The former 254 count included the
  synthetic SPDX root and omitted those three Google contractual records.
- `compose.qualify.yaml` preserves separate emulator and pinned Appium services,
  `mobile` and `appium-ingress` networks, internal identity
  `android-emulator:5555`, and loopback-only Appium exposure.
- `qualify.ps1` runs the real smoke, proves zero skips, always tears down, checks
  for labeled residual resources, and keeps clean-run and controlled-failure
  evidence separate.
- The required `.github/workflows/pull-request.yml` `appium-smoke` lane builds
  the Google-based MOB-003 image locally without cache, retains SBOM and
  max-mode provenance attestations in the runner-local containerd image store,
  checks KVM, starts `compose.qualify.yaml`, enforces a real zero-skip smoke,
  captures failure diagnostics, and always tears down. It does not push,
  publish, transfer, adopt the image in production Compose, or use privileged
  mode.
- Ephemeral CI means the image stays in one isolated runner-local Docker store,
  no image/layer/cache is uploaded or shared, teardown is unconditional, and
  the worker is destroyed or removes the layers before reuse. Push, OCI/tar
  export, registry/cache publication, release attachment, or copying/loading to
  another host or shared daemon is prohibited under the current model.
- MOB-003 is a framework-development health checkpoint. Production Compose,
  framework APIs, dependencies, Java, and Spring Boot contracts are unchanged.
  Consumers remain responsible for providing mobile infrastructure through the
  existing Appium configuration contract; the qualified image is not delivered
  to them.

## 5. Supply-chain evidence

Evidence is under ignored `target/mob-003/` with hashes in
`evidence-manifest.sha256`:

- `candidate-a.spdx.json`: valid SPDX 2.3, 253 packages.
- `candidate-a.provenance.json`: verified BuildKit provenance with all seven
  immutable materials and linux/amd64 platform.
- `candidate-a-vulnerabilities.sarif`: 32 findings against the exact final
  digest, all classified `CONTROL_PLANE` with path evidence.
- `builder-python-vulnerabilities.sarif`: 128 findings against the exact builder
  digest, all classified `BUILDER` with path evidence; builder contents are
  absent from runtime and outputs are bound to verified inputs, provenance, and
  the final digest.
- `candidate-a-NOTICE.bundle.txt`: exported emulator, platform-tools, and system
  image notices.
- `android-sdk-license-acceptance.txt`: explicit build-time acceptance record;
  it does not claim distribution approval.
- `candidate-a-license-inventory.csv`: 256 machine-readable records, all
  `ALLOWED` under `MOB-003-GOV-001`. All 38
  original `NOASSERTION` rows were resolved or, for the synthetic image root,
  removed as a duplicate aggregate. The three Google artifacts are explicit
  contractual exceptions.
- `candidate-a-noassertion-resolutions.csv`: reproducible proof containing all
  38 original SPDX IDs, 37 evidence-backed mappings, and the one removed
  synthetic aggregate; zero `NOASSERTION` remains.
- `candidate-a-sec-003.properties`: complete policy evidence with 160 findings
  and 256 license records.
- `candidate-a-sec-003-result.json`: `PASS`, with zero reasons and zero
  `UNKNOWN`-plane findings; SHA-256
  `aed8698e61a0e9fca2157f3fcbf032a7511bd2d093657f6f6c5406208cf8f934`.
- `product-owner-approval.txt`: binds `MOB-003-GOV-001`, report SHA-256, exact
  image digest, `ALLOWED` disposition, and count 256; SHA-256
  `5156ef26bdf68264aa42f1c7c5ff23e5feedbd3637442b14d27570c3dcbcdea3`.
- Post-approval `evidence-manifest.sha256`: SHA-256
  `56a4e01f7f5e468c536407016e3008dfa0abce1dcb21e4d9d262cd80755d3f7f`.

Docker Scout identity is version 1.24.0, commit
`b1c9331b2166aef7ec690aa16fd655b8798ea4c6`, scan date 2026-08-27. Its CLI does
not expose an immutable advisory-database revision; the evidence records the
service-managed database identity and this limitation rather than inventing a
revision.

The approval request is
`docs/assignments/MOB-003-approval-package.md`; the exception-focused analysis
is `docs/assignments/MOB-003-license-obligation-report.md`.

The completed GitHub evidence record is
`docs/assignments/MOB-003-github-qualification-evidence.md`. Its source archive
is `docs/assignments/logs_90050184653.zip`, SHA-256
`d4c8a512745097952c2f8a473d84a4b2b0ff2acc20094f2ce394d03f9a8df392`.

## 6. Runtime and cleanup results

- Docker's WSL-backed Linux environment exposes usable `/dev/kvm`; UID 10001
  had read/write access and emulator acceleration started.
- ADB reached `device`; boot completion was `1`; Android reported release 14 and
  SDK 34.
- Emulator and Appium both became healthy; Appium resolved and reached
  `android-emulator:5555` through the intended networks.
- Clean run 1: `AndroidEmulatorSmokeTest` 1 run, 0 failures, 0 errors, 0 skips;
  no labeled container or network remained.
- Clean run 2: `AndroidEmulatorSmokeTest` 1 run, 0 failures, 0 errors, 0 skips;
  no labeled container or network remained.
- Controlled failure after Appium/device preflight: diagnostics captured;
  unconditional teardown left no labeled container or network.
- GitHub PR run `90050184653` on 2026-08-29 UTC used Linux `amd64` with usable
  KVM, built `codinglair-taf/android-emulator:mob-003-api34` from the Google
  recipe, and produced runner-local image ID
  `sha256:f1fb4bdb5b4d7994c0372e287b3e2aef05c4727a608a9469c71b819df21e9672`.
  BuildKit generated the SBOM and exported attestation manifest
  `sha256:4a5d36c7b7caa96e6a8d8e1dba503f471afe50c4bfbe6d09f287a12932f82792`.
- The GitHub emulator and Appium services became healthy. The smoke used
  `android-emulator:5555` and `AndroidEmulatorSmokeTest` completed with 1 test,
  0 failures, 0 errors, and 0 skips. Cleanup recorded empty remaining-container
  and remaining-network sets.
- The user-provided manual smoke is preserved at
  `docs/assignments/MOB-003-android-smoke-evidence.txt` and independently shows
  healthy services, both networks, a zero-skip candidate smoke, and successful
  teardown.

An unrelated Runtime Core test remains conditionally skipped; it does not alter
the candidate-specific zero-skip result, which is also enforced by
`SmokeReportCheck`.

## 7. Verification performed

- Clean Candidate A build with `build.ps1 -AcceptAndroidSdkLicense -Clean`:
  PASS, exact digest above.
- Exact final-image Docker Scout scan: PASS, 32 findings recorded.
- Exact builder-base Docker Scout scan: PASS, 128 findings recorded.
- `complete-evidence.ps1 -ApprovalReference MOB-003-GOV-001
  -ApprovedLicenseReportSha256 105a5a...2164`: PASS; all original
  `NOASSERTION` records resolved and all 256 dispositions approved.
- Independent `ImagePolicy` execution: exit code 0; JSON `result=PASS`; zero
  reasons.
- `verify.ps1`: PASS.
- `verify-compose.ps1`: PASS.
- `qualify.ps1 -Runs 2`: PASS twice with candidate smoke zero skips and clean
  teardown.
- `qualify.ps1 -Runs 1 -ControlledFailure`: PASS with clean teardown.
- `ImagePolicyTest`: PASS, 20 scenarios.
- `WorkflowContractTest`: PASS, including the manual MOB-003 workflow boundary.
- `git diff --check`: run at final checkpoint.
- GitHub `Pull Request Verification`, run
  [90050184653](https://github.com/codinglair/codinglair-taf/actions/runs/90050184653),
  PR `#74`, tested merge commit
  `9b5ca6906c25e21d69f99abdeb90fd5948ff0795`: PASS. The selected
  `Android emulator Appium smoke` job passed with the Google qualification
  recipe, SBOM/provenance, healthy services, one zero-skip emulator smoke, and
  successful cleanup. The aggregate `PR gate` passed.

## 8. Acceptance status

- PASS — all immutable inputs, checksums, exact digest, platform, non-root and
  non-privileged controls.
- PASS — SBOM, provenance, final-image scan, builder scan, notice bundle, SDK
  acceptance record, complete vulnerability classification, and evidence
  checksums.
- PASS — local boot/API, Appium health, topology, two real zero-skip smokes,
  successful teardown, and controlled-failure teardown.
- PASS — required GitHub PR qualification and aggregate PR Gate for the
  recorded merge commit.
- PASS — Product Owner governance decision `MOB-003-GOV-001` approves the
  restricted reference-qualification model and exact reviewed evidence.
- PASS — Candidate A SEC-003 exits 0 with JSON `result=PASS` and zero reasons.
There is no remaining MOB-003 qualification gate. DEVOPS-001R may integrate the
reference qualification recipe and its health gate—not a distributable image
or image digest. The image must remain ephemeral and runner-local; do not
publish, transfer, deliver, adopt it in production Compose, or treat either the
local or GitHub qualification digest as a consumer-delivery contract.

The requested approval is governance approval of the operating model and
controls, not a legal opinion. If an authorized legal review actually occurs,
record reviewer identity/role, scope, date, and decision reference; otherwise
the handoff must not claim legal approval.

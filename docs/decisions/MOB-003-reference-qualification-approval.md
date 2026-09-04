# MOB-003 Reference Qualification Recipe Approval

**Decision ID:** MOB-003-GOV-001
**Decision date:** 2026-08-28
**Decision owner:** Codinglair repository and Product Owner
**Decision status:** APPROVED WITH RESTRICTIONS

## 1. Decision

I approve the MOB-003 controlled Android 14/API 34 emulator build recipe for use solely as an ephemeral development and CI qualification environment for Codinglair TAF.

This approval covers:

* The Apache-2.0-licensed Dockerfile, scripts, qualification configuration, and documentation stored in the Codinglair TAF repository.
* Checksum-pinned downloading of the specified Google Android SDK artifacts during an authorized local or CI build.
* Temporary construction of the emulator image in the builder’s local Docker content store.
* Execution of the reference Appium smoke test against that temporary environment.
* Collection of non-image evidence, including logs, test reports, SBOM, provenance, scan results, hashes, notices, and build metadata.
* Destruction of the emulator containers, networks, generated image, image layers, and non-permitted caches after qualification.

The controlled emulator image is a framework-development health checkpoint. It is not a Codinglair TAF product artifact, runtime dependency, supported consumer emulator, or consumer testing-infrastructure offering.

## 2. Consumer infrastructure boundary

Codinglair TAF consumers are responsible for providing and configuring their own mobile testing infrastructure, including one or more of the following:

* Physical mobile devices.
* Locally managed emulators.
* Existing Appium servers or grids.
* Private device laboratories.
* Commercial or cloud device farms.

The framework must remain configurable against consumer-provided Appium endpoints and device capabilities. It must not require the MOB-003 reference emulator image.

## 3. Approved Google SDK use

The approved recipe may download the following pinned Google artifacts:

* Android Emulator 37.1.11, build 15917651.
* Android platform tools 37.0.1.
* Android 14/API 34 Google APIs x86_64 system image revision 14.

Their exact URLs and SHA-256 checksums must remain those recorded by the approved MOB-003 evidence package.

Use of these artifacts requires:

1. Explicit acceptance of the Android SDK License Agreement by the authorized builder.
2. Preservation of applicable copyright, proprietary-rights, and third-party notices.
3. Use only for the approved Android development and test-qualification purpose.
4. No publication or distribution of the assembled emulator image.
5. A new review if the artifacts, checksums, governing terms, or operating model change.

This decision is based on the Android SDK License Agreement displayed as dated April 28, 2026 and reviewed on August 28, 2026:

`https://developer.android.com/studio/terms`

A material change to those terms invalidates the Google-artifact disposition for future builds and requires renewed review.

The Android SDK terms are accepted by the repository owner in an individual capacity. “Codinglair” identifies the project and brand and is not represented by this decision as a separate legal entity.

## 4. License and obligation disposition

I approve the license classifications and compliance controls documented in:

* `MOB-003-license-obligation-report.md`
* Report SHA-256: `105a5a361747e3de68457ae14ae63506bb84b4ad42d9316c98dea8d00a962164`
* `candidate-a-license-inventory.csv`
* Inventory SHA-256: `76c2d7d1129d503e35b3a9e2ef0b81a5c4f8379bbc66ea783ce65524c9df5586`
* `candidate-a-noassertion-resolutions.csv`
* Resolution evidence SHA-256: `1cec5e2c22f781d1e97381d9da8986d1ba5e4100ae5620afb3a8785a9c7cea15`
* `candidate-a-NOTICE.bundle.txt`
* Notice bundle SHA-256: `418e4b02c286f934b5e2c28bf33b633a1bd8ddb9a83499a098d4b95388d4d058`
* Evidence manifest SHA-256: `b05e67335cb80025bdc9ad4c8227a96525bd0967c93f9ace77dd2c1b57cd8b6a`

This approval permits SEC-003 to assign `ALLOWED` to the reviewed license records only for the operating model and exact evidence identified by this decision.

It does not state that every component is Apache License 2.0. Repository-authored material is Apache-2.0, while Google SDK artifacts remain governed by the Android SDK agreement and included notices, and Ubuntu/Android components remain governed by their respective licenses.

The documented notice-preservation controls are required. Corresponding-source, relinking, modification-disclosure, or other distribution obligations must be reevaluated before any generated image is conveyed.

## 5. Exact qualified candidate

The evidence evaluated by this decision applies to:

* Candidate: Android 14/API 34 Google APIs x86_64 revision 14.
* Platform: `linux/amd64`.
* Local qualification OCI digest:
  `sha256:a60cc06baa93451336aa97b36540e5136b77be72019be76dcf8da7df53b4cebf`

This digest identifies the locally qualified build. It is not an approved distribution or release digest.

Changes to the Dockerfile, base images, Google artifacts, installed packages, build inputs, final image digest, license inventory, or evidence package require renewed evaluation and approval.

## 6. Ephemeral CI requirements

An approved CI qualification run must:

* Build the image from pinned inputs without importing a prebuilt candidate image.
* Require explicit Android SDK license acceptance.
* Keep the generated image and layers in the runner-local Docker content store.
* Run the real zero-skip Appium qualification.
* Upload only non-image evidence.
* Remove containers, networks, generated images, layers, and non-permitted caches unconditionally.
* Verify cleanup after successful and failed execution.
* Use an ephemeral hosted runner or an equivalently isolated worker that deletes the image and layers before reuse.

## 7. Prohibited actions

This approval does not authorize:

* `docker push`.
* `docker save` or OCI image export.
* Registry storage, whether public or private.
* Uploading the image or its layers as CI artifacts.
* Shared or remote BuildKit caches containing Google SDK payloads.
* Release attachments containing the image or Google artifacts.
* Copying or loading the image onto another host.
* Use through a shared persistent Docker daemon.
* Supplying the image to framework consumers.
* Marketing the emulator image as a Codinglair product or supported runtime.
* Incorporating the image as a mandatory framework dependency.
* Production or customer deployment of the image.
* Resuming DEVOPS-001R before the remaining SEC-003 and GitHub qualification gates pass.

Any proposed image transfer or distribution requires a separate review and approval.

## 8. Approval limitations

This is a Codinglair project-governance and Product Owner decision based on the recorded technical evidence. It is not an opinion or approval from independent legal counsel.

Independent legal review is required before public or customer image distribution, commercial image delivery, hosted emulator services, or another materially different distribution model.

## 9. Required post-approval gates

This approval becomes operational only after:

1. The final hashes above are recorded and verified.
2. `complete-evidence.ps1` is executed with approval reference `MOB-003-GOV-001`.
3. SEC-003 exits with code `0`.
4. SEC-003 structured output reports `result=PASS`.
5. The prepared manual GitHub qualification completes successfully.
6. The GitHub smoke contains zero skips.
7. CI cleanup and residual-resource checks pass.
8. The final MOB-003 handoff records the evidence accurately.

This approval alone does not complete MOB-003 or authorize DEVOPS-001R to resume.

**Approved by:** Codinglair repository and Product Owner
**Approval reference:** MOB-003-GOV-001
**Approval date:** 2026-08-28
**Signature or recorded confirmation:** 
Confirmed by: codinglair (repository owner)
Confirmation: I approve MOB-003-GOV-001 with the restrictions and post-approval gates stated above.
Date: 2026-08-28

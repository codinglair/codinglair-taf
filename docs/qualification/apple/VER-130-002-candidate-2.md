# VER-130-002 Apple qualification record — candidate 2

**Record date:** 2026-10-07  
**Record policy:** immutable hosted result appended after candidate 1  
**Implementation:** COMPLETE  
**Code review:** COMPLETE  
**Architectural review:** COMPLETE  
**Hosted Apple simulator qualification:** **PASSED**  
**Physical-device qualification:** **UNVERIFIED**

This record captures the first passing hosted Apple simulator qualification for the VER-130-002
candidate. It does not alter the historical candidate-1 `NOT RUN / UNVERIFIED` record. The Apple
qualification and general pull-request verification are separate workflows and both passed against
the same candidate commit.

## Hosted run identity

| Field | Observed value |
| --- | --- |
| Repository / pull request | `codinglair/codinglair-taf` / PR 91 |
| Candidate branch | `feature/VER-130-002` |
| Base branch | `release/v1.3.0` |
| Tested source SHA | `ff1cf9d0ede23737966418665a9739356bb3e846` |
| Commit subject | `feat: Add support for additional Web Inspector application identifiers in hybrid sessions` |
| Apple workflow | `VER-130-002 Apple Simulator Qualification` |
| Run / attempt | [37695433922](https://github.com/codinglair/codinglair-taf/actions/runs/37695433922) / 1 |
| Job | [`qualify` / 113045891290](https://github.com/codinglair/codinglair-taf/actions/runs/37695433922/job/113045891290?pr=91) |
| Trigger | `pull_request` |
| Runner label | `macos-15` |
| Started / completed | 2026-10-07 22:19:35Z / 22:35:33Z |
| Job conclusion | `success` |
| Companion PR workflow | [Pull Request Verification 37695434483](https://github.com/codinglair/codinglair-taf/actions/runs/37695434483), attempt 1, `success`, same tested SHA |

The public GitHub Actions API reports every Apple job step as successful: checkout, Java setup,
Node setup, ffmpeg installation and verification, the real simulator smoke, runner-metadata
recording, and sanitized evidence upload.

## Retained evidence

| Field | Observed value |
| --- | --- |
| Artifact | `apple-simulator-qualification-evidence` |
| Artifact ID | `11515947412` |
| Size | 13,258,015 bytes |
| Archive digest | `sha256:12c90dff4bfa9f45ea61dc93cadcd96bd30c82d5abdb846df14f47799900ba65` |
| Created | 2026-10-07 22:35:09Z |
| Scheduled expiry | 2026-10-21 22:35:06Z |
| API status when recorded | available; not expired |

The retained artifact is the authority for the exact runner image version, macOS build, Xcode/SDK,
npm, WDA package hashes, candidate artifact hashes, fixture hash, working-tree diff hash, simulator
UDID, test reports, screenshots, page sources, context observations, video/log availability, and
cleanup diagnostics. GitHub requires authenticated artifact download; values not exposed by the
public run metadata are deliberately not reconstructed or guessed in this record.

## Qualified compatibility boundary

The successful workflow enforced the following selected tuple before it could record
`hostedStatus=PASSED`:

| Dimension | Qualified value |
| --- | --- |
| TAF candidate | 1.2.0 artifacts built from the tested checkout into a job-local repository |
| Appium Java client / Selenium | 10.1.1 / 4.43.0 |
| Java | 25 |
| Node | 22.12.0 |
| Appium / XCUITest driver | 3.0.0 / 10.0.0 |
| Xcode / simulator SDK selection | Xcode 16.4 / iOS 18.5 |
| Target | iPhone 16, iOS 18.5 simulator, job-owned UDID |
| Device family / kind | IPHONE / SIMULATOR |
| Topology | LOCAL_HOST, loopback Appium and deterministic page server |
| Application modes | native, hybrid WKWebView, and Mobile Safari |
| Hybrid Web Inspector identity | exact additional identifier `process-TafAppleFixture` |

Passing setup also proves that the required `ffmpeg` executable was installed, resolved from
`PATH`, and executed before the live smoke. No video feature was disabled to obtain the pass.

## Executed outcomes

| Evidence area | Hosted outcome |
| --- | --- |
| Native fixture interaction and terminate/reactivate behavior | PASSED |
| Hybrid WKWebView load, context discovery, DOM interaction, native return | PASSED |
| Mobile Safari deterministic loopback-page interaction | PASSED |
| Required nonempty screenshot and page-source evidence | PASSED |
| Genuine device-log and video collection | ATTEMPTED; exact availability retained in the artifact |
| Controlled failure preserved as the primary failure | PASSED |
| Controlled-failure owned Appium session deletion and invalid-session verification | PASSED |
| Job-owned simulator/process cleanup | PASSED workflow cleanup; detailed diagnostics retained |
| Sanitized qualification evidence upload | PASSED |

These outcomes follow from the fail-closed runner and successful `Execute real Apple simulator
smoke` step: the runner exits nonzero if any required primary test, evidence assertion, controlled
failure expectation, session-specific cleanup verification, or compatibility check fails.

## BRD §33.4 evidence disposition

| Point | Candidate-2 disposition |
| --- | --- |
| 1 — compatibility boundary | PASSED for the selected tuple; exact retained metadata listed above |
| 2 — native/hybrid/Safari | PASSED on the real hosted simulator |
| 3 — Android-derived lifecycle verification | PASSED for Apple; companion PR verification also passed |
| 4 — customer-managed topology | PASSED for selected LOCAL_HOST simulator topology only |
| 5 — evidence | Required screenshot/source passed; optional log/video availability retained |
| 6 — consumer/MCP | Standalone candidate consumer passed; prior VER-130-001 MCP evidence remains authoritative |
| 7 — isolation/cleanup | PASSED for job-owned simulator and owned-session controlled-failure cleanup |
| 8 — Android compatibility | Companion PR verification passed; prior VER-130-001 evidence remains authoritative |
| 9 — security | PASSED within loopback, no-secret, bounded-artifact workflow boundary |
| 10 — truthful release evidence | PASSED; this append-only record replaces no historical claim |

## Qualification bounds

This result qualifies only the tested SHA and selected hosted simulator tuple. It does not certify
physical devices, iPad, other iOS/macOS/Xcode combinations, signing or provisioning, cloud-device
providers, remote customer hosts, concurrent external jobs, or every application. Physical-device
status remains **UNVERIFIED**. Any subsequent change to Runtime mobile code, the Apple consumer,
fixture, runner, or workflow requires a new hosted run and another append-only candidate record.

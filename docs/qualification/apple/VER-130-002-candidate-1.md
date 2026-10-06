# VER-130-002 Apple qualification record — candidate 1

**Record date:** 2026-10-06  
**Record policy:** immutable candidate record; append a new numbered record after any hosted run or
candidate-code change  
**Implementation:** COMPLETE  
**Code review:** COMPLETE  
**Architectural review:** COMPLETE  
**Hosted Apple simulator qualification:** **NOT RUN / UNVERIFIED**  
**Physical-device qualification:** **UNVERIFIED**

This record does not certify a hosted run. It freezes the selected compatibility tuple and the
executable evidence design delivered by VER-130-002. A Product Owner must append a new record with
observed values; never replace pending fields in this historical candidate record.

## Selected tuple and pending observations

| Dimension | Selected candidate | Actually observed |
| --- | --- | --- |
| Source | Candidate branch checkout; exact SHA and diff hash recorded by runner | PENDING |
| TAF/client | TAF 1.2.0 candidate artifacts; Appium Java client 10.1.1 | PENDING |
| Selenium | Selenium 4.43.0 BOM | PENDING |
| Java | Temurin 25 | PENDING |
| Hosted image | GitHub `macos-15`; mutable label, runner image version retained at run time | PENDING |
| Xcode / SDK | Xcode 16.4 / iOS 18.5 simulator SDK | PENDING |
| Node / npm | Node 22.12.0 / hosted setup npm, exact observed npm retained | PENDING |
| Server / driver | Appium 3.0.0 / XCUITest 10.0.0 | PENDING |
| WDA | Dependency resolved by XCUITest 10.0.0; installed metadata/log retained | PENDING |
| Target | iPhone 16 simulator, iOS 18.5, job-owned UDID | PENDING |
| Fixture | Synthetic `com.codinglair.taf.fixture`; executable SHA-256 retained | PENDING |
| Native topology | local Appium on loopback, preinstalled simulator fixture | PENDING |
| Hybrid topology | same fixture with one inspectable WKWebView and explicit context selection | PENDING |
| Safari topology | loopback static deterministic page | PENDING |
| Evidence | screenshot and page source required; syslog/video attempted with availability outcomes | PENDING |

No dependency was added or upgraded in the repository. Appium/XCUITest are workflow tools installed
only in the ephemeral job. Candidate Java artifacts are built from the checkout and deployed to a
job-local Maven repository before the standalone consumer resolves them.

## BRD §33.4 evidence register

| Point | Implemented evidence | Hosted outcome |
| --- | --- | --- |
| 1 — compatibility boundary | Exact selected/observed manifest fields and artifact hashes | NOT RUN |
| 2 — native/hybrid/Safari | Three real TestNG cases through named public `AppleController` instances | NOT RUN |
| 3 — Android-derived verification | TestSession lifecycle, real process boundary, explicit cleanup and retained diagnostics | NOT RUN |
| 4 — customer-managed topology | Loopback local-host Appium; remote/provider remain implemented but unverified here | NOT RUN |
| 5 — evidence | Nonempty screenshot/source assertions; genuine syslog/video availability metadata | NOT RUN |
| 6 — consumer/MCP | Standalone consumer and candidate repository; VER-130-001 MCP evidence remains authoritative | NOT RUN |
| 7 — isolation/cleanup | Job-owned simulator plus expected-failure run requiring zero remaining sessions | NOT RUN |
| 8 — Android compatibility | VER-130-001 Android regression evidence; no Android contract changed here | REFERENCED |
| 9 — security | Loopback services, no credentials/signing secrets, bounded retained artifacts | NOT RUN |
| 10 — truthful release evidence | Pending status and immutable append-only update procedure | PASS (document structure) |

Local structural and compilation outcomes belong in the assignment handoff. They are not simulator
qualification. Existing VER-130-001 evidence remains classified as protocol/consumer/Android
regression and does not become Apple live evidence by reference.

## Product Owner hosted-run procedure

The workflow has both `workflow_dispatch` and a narrowly scoped `pull_request` trigger. GitHub only
offers manual dispatch for workflow files present on the default branch, so before merge use the PR
trigger by opening/updating a pull request containing the workflow, or push the candidate workflow
to an appropriately governed branch already exposed for manual dispatch. Do not merge to `master`
merely to obtain evidence.

After the run, download `ver-130-002-<run-id>-<attempt>`, verify the workflow conclusion and retained
outcome/cleanup/test reports, and append `VER-130-002-candidate-2.md` containing run URL, ID, attempt,
tested SHA, diff hash, candidate artifact hashes, fixture hash, observed runner/macOS/Xcode/SDK,
Node/npm/Appium/XCUITest/WDA, simulator tuple, each test outcome and artifact/cleanup outcomes. A
setup failure, executed test failure, success, and not-executed test must retain distinct statuses.

Any change to Runtime mobile code, this consumer, fixture, runner or workflow after a run invalidates
the live result and requires all native, hybrid, Safari, evidence and controlled-failure cases to be
rerun. Documentation-only corrections that do not alter claims may append a correction record.

## Bounds and unverified configurations

This single simulator tuple does not certify iPad, physical devices, signing/provisioning, other iOS
or macOS/Xcode versions, remote customer hosts, device-cloud providers, concurrent external jobs, or
every application. Those configurations are implemented where stated by prior assignments but are
unverified by this record. Upstream or environment-specific unsupported logs/video remain explicit
artifact outcomes and are not silently converted to success.

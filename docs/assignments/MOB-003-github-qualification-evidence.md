# MOB-003 GitHub Qualification Evidence

## Disposition

> **MOB-003 GitHub qualification: PASSED.**  
> The required PR Gate built the controlled Google-based Android API 34 emulator image locally, started it through the qualification Compose configuration, completed the Appium smoke test with zero skips, and performed cleanup successfully. The aggregate PR Gate passed for the recorded commit.

**Final disposition:** MOB-003 is qualified and completed.

## Run identity

| Field | Recorded value |
|---|---|
| GitHub workflow | `Pull Request Verification` |
| Workflow run | [codinglair/codinglair-taf run 90050184653](https://github.com/codinglair/codinglair-taf/actions/runs/90050184653) |
| Workflow run ID | `90050184653` |
| Pull request | `#74` |
| Tested commit | `9b5ca6906c25e21d69f99abdeb90fd5948ff0795` (`refs/pull/74/merge`, the PR merge commit checked out by every recorded job) |
| Run date | `2026-08-29` UTC (`01:05:21Z` through `01:19:12Z` in the archived logs) |
| Runner environment | GitHub-hosted Ubuntu 24.04, Linux `amd64`/`x86_64`; `/dev/kvm` was mapped with dynamic supplementary GID `993`, and `emulator -accel-check` reported `KVM (version 12) is installed and usable.` |

## Jobs and aggregate result

The archive records these workflow jobs:

- `Change impact` — success
- `Unit tests (Java 25)` — success
- `Affected integration and contract tests` — success
- `Cross-module smoke` — success
- `Browser smoke` — success
- `Android emulator Appium smoke` — success
- `PR gate` — passed; it reported all selected lanes as `success` and concluded: `All required pull-request lanes have acceptable terminal results.`

## MOB-003 image and supply-chain evidence

| Field | Evidence |
|---|---|
| Local image tag | `codinglair-taf/android-emulator:mob-003-api34` |
| Resulting image ID / manifest-list digest | `sha256:f1fb4bdb5b4d7994c0372e287b3e2aef05c4727a608a9469c71b819df21e9672` |
| Image manifest digest | `sha256:e2a5d9cd37c24214f4c0332335c3c168157420cdfa3ee2e9f52796b519fd812e` |
| Attestation manifest digest | `sha256:4a5d36c7b7caa96e6a8d8e1dba503f471afe50c4bfbe6d09f287a12932f82792` |
| Recipe used | The job invoked `containers/android-emulator/google/build.ps1`, built the MOB-003 tag locally, and used `containers/android-emulator/google/compose.qualify.yaml` for KVM verification, startup, and teardown. This is the controlled Google-based Android 14/API 34 recipe. |
| Legacy image exclusion | No `budtmo` reference occurs anywhere in the archived run logs. The locally built MOB-003 tag is the emulator image recorded by the build, and the qualification Compose file is the only emulator Compose configuration invoked by the Appium lane. |
| SBOM and provenance | Passed. BuildKit generated the SBOM with the pinned Syft scanner `docker/buildkit-syft-scanner@sha256:187e1892a7752c9384c59aba9517dd8e40610b748c72773e87b63720514463c2`, exported the attestation manifest successfully, and completed the `--provenance=mode=max` build. The image remained runner-local; the logs contain no push or image transfer. |

## Runtime and smoke evidence

| Check | Result |
|---|---|
| Emulator health | Passed. Compose reported `codinglair-taf-mob-003-android-emulator-1` as `Healthy`. |
| Appium health | Passed. Compose reported `codinglair-taf-mob-003-appium-1` as `Healthy`. |
| ADB identity | The Appium smoke used the Compose-network identity `android-emulator:5555`. The MOB-003 emulator-side health topology uses loopback ADB `127.0.0.1:5557`; it is not substituted for the Appium-facing identity. |
| Appium smoke | `AndroidEmulatorSmokeTest`: **tests 1, failures 0, errors 0, skipped 0**; elapsed time `23.55 s`. The subsequent `SmokeReportCheck` step completed successfully, enforcing actual execution and zero skips. |
| Cleanup | Passed. Qualification Compose teardown removed the Appium and emulator containers and both project networks. The recorded post-cleanup values were `remaining_containers=` and `remaining_networks=`, and both empty-value assertions passed. |
| Overall PR Gate | **PASSED.** The Appium lane was selected (`true`) and reported `success`; every other required lane also reported `success`. |

## Archived evidence identity

| Field | Value |
|---|---|
| Pipeline ZIP | `docs/assignments/logs_90050184653.zip` |
| ZIP filename | `logs_90050184653.zip` |
| SHA-256 | `d4c8a512745097952c2f8a473d84a4b2b0ff2acc20094f2ce394d03f9a8df392` |

The checksum was calculated from the repository copy reviewed for this record. The archive includes the combined logs and per-step logs for all seven jobs listed above.

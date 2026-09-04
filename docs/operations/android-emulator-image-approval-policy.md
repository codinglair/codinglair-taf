# Android emulator image approval policy

`build-support/ci/ImagePolicy.java` is the authoritative, fail-closed evaluator. This document explains its input; it does not duplicate its decision logic. Run it with Java 25:

```text
javac -d target/ci-support build-support/ci/ImagePolicy.java
java -cp target/ci-support ImagePolicy candidate.properties
```

The deterministically parsed UTF-8 evidence format is one `key=value` per line. Blank lines and `#` comments are allowed; duplicate, empty, or malformed fields are rejected. Counts use zero-based records (`artifact.count`, `finding.count`, `license.count`). The evaluator emits JSON with `PASS` or `REJECT`, reasons, and findings grouped as `BUILDER`, `CONTROL_PLANE`, `ANDROID_GUEST`, and `UNKNOWN`.

Required candidate fields are name, purpose, target OS/architecture, matching declared and scanned final digests, exact builder/runtime base digests, confirmation that builder bases were scanned, immutable source repository/commit, scanner/version/database identity/date, and the high-severity rejection setting. Every downloaded artifact requires URL, version, and SHA-256. SPDX SBOM and verified provenance locations and checksums are mandatory. Notices and the Android SDK acceptance record are mandatory. Evidence must contain no credentials, tokens, private keys, or environment dumps.

Each finding records ID, component, severity, fix availability, plane, and reproducible classification evidence prefixed by `path:`, `layer:`, `sbom:`, or `mapping:`. Package name alone is not evidence. Builder findings also record final-runtime presence and cryptographic output verification. Control-plane fixable Critical and High findings reject. Guest findings record host-escape, cross-service, credential/data impact, and whether the component is intrinsic to the selected Android version. Guest disposition additionally requires non-public ADB, approved Appium binding, no production credentials/data/connectivity, ephemeral data, and no privileged execution. `UNKNOWN`, missing, ambiguous, or contradictory evidence rejects.

Each license record identifies the component, exact detected license, obligations, scope (`PROJECT_AUTHORED`, `DOWNLOADED_SDK`, or `ANDROID_SYSTEM_IMAGE`), and disposition. `UNKNOWN`, `UNREVIEWED`, `CONFLICTING`, and `PROHIBITED` reject; a raw record count cannot decide approval. Upstream notices must be preserved. This policy records technical disposition and never constitutes legal approval. Generated Android images must not be published until redistribution obligations receive explicit disposition.

Builder images and dependencies remain pinned and scanned even when absent from runtime. A vulnerable builder rejects when it can alter an output that is not cryptographically verified. Neither previously assessed `budtmo/docker-android` digest is approved by this policy; reassessment requires a complete new evidence file.

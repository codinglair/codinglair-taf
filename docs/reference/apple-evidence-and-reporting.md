# Conditional Apple evidence and reporting

Apple evidence uses the existing `collectArtifacts(ArtifactReason)` hook and the
session `ArtifactCollector`. Returned artifacts are already published to that
collector; consumers must not add them again. Reporter finalization attaches the
sanitized artifacts through the neutral reporting SPI. Android behavior is unchanged.

| Evidence | When requested | Conditional outcome |
| --- | --- | --- |
| Screenshot | FAILURE, CLEANUP_FAILURE or EXPLICIT with screenshot-on-failure=true | Requires allow-visual-artifacts; empty or policy-suppressed content is unavailable |
| Page source | Same reasons with page-source-on-failure=true | Requires the existing visual/source policy; sensitive source is suppressed by authenticated transport when reliable masking is unavailable |
| Device/system logs | Each collection with device-logs=true | Requests only advertised XCUITest syslog; absent syslog is unsupported; server logs and Android Logcat are never substituted |
| Video | video=true starts recording after session creation; first collection stops/fetches it | Unsupported start does not fail default execution; empty or suppressed recording is unavailable; supported retrieval errors are collection-failed |
| Context/version metadata | Each permitted collection | Retains native/web category, Apple family and numeric platform version only; raw WebView identifiers/capabilities are omitted |

Each requested type emits a diagnostic artifact containing `artifact=<type>;
outcome=<available|unsupported|unavailable|collection-failed>`. Available payloads
have their normal artifact type and MIME type. Outcome metadata has no raw errors,
host/device identifiers, credential values or artifact URLs. Availability does not
change controller health/readiness.

The existing defaults are screenshot-on-failure=true, page-source-on-failure=true,
device-logs=true, video=false and allow-visual-artifacts=false. The additive
`taf.mobile.apple.require-evidence` property defaults to false. Setting it to true
makes any nonavailable requested artifact (including context metadata or an
exhausted capture budget) fail collection after recording all outcomes. Named
controllers inherit it; an explicit false overrides a stricter base setting.
Strictness does not change passive readiness or force supported video to exist.
Previously captured video remains available on later collections without another
stop request or duplicate payload attachment.

Use FAILURE collection in the consumer's failure boundary before closing its
TestSession, for example in a catch/finally block around the test operation.
The existing runner lifecycle has no automatic controller FAILURE callback;
this assignment preserves it. Close automatically collects DIAGNOSTIC evidence
(context, logs and final recording) before app cleanup and driver quit. Capture
failure cannot skip cleanup, and close remains idempotent. With strict policy,
cleanup still runs before a sanitized close failure is propagated through the
existing runner suppression mechanism.

Recording is limited to two minutes. Each controller permits four capture batches
and one bounded budget-exhaustion diagnostic, with a two-MiB decoded binary/text
limit per payload. The trusted Appium transport separately bounds response bodies
to four MiB and each request to the configured transport deadline. Oversized data
is rejected rather than persisted in part. Artifacts have a unique controller/session
scope and batch number, so separate named controllers cannot overwrite each other's
structured results. No second filesystem store or retention mechanism is introduced;
existing collector, reporter and artifact-store retention rules remain authoritative.

Provider video URLs are fetched inside the existing trusted transport before
redaction. The resource authorizer must grant the exact normalized
ARTIFACT_DESTINATION origin/path for the initial URL and each redirect. The shared
ArtifactDownloadTransport enforces bounds, redirect limits and TLS downgrade
denial. Signed query parameters exist only on the wire. Appium credentials are
never forwarded to artifact hosts. Standalone trusted Appium settings do not
implicitly authorize provider artifact destinations. Authenticated sessions whose
visual content cannot be reliably scrubbed retain unavailable outcomes instead
of unsafe payloads, following SEC-130-001 policy.

Protocol fixtures verify available/unsupported/unavailable/collection-failed
outcomes, neutral reporter delivery, strict policy, storage/capture bounds,
provider destination trust, redaction, separation and cleanup ordering. These
checks do not certify a provider, actual device logs or real video. Real Apple
infrastructure qualification remains VER-130-001/002 and release-owner work.

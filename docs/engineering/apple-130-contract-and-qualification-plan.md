# Apple 1.3.0 contract inventory and qualification plan

Date: 2026-10-01. Owner: MOB-130-001. Source baseline:
`9fa7f9cb7a3b9d6889f02efe591c9a0b508d8f69` plus the reviewed working-tree changes.
This is a contract and verification plan, not a claim of implemented Apple support.

## Authority and status reconciliation

The pre-existing working-tree ADR edits record Product Owner acceptance of
ADR-031â€“035 on 2026-10-01. This assignment preserves that recorded disposition;
it does not independently assert or grant approval. Their prior proposed labels
are historical; implementation and architectural review still apply.
BRD 1.4 Â§16.1 and all ten Â§33.4 acceptance points, SAD 1.13 Â§8.4, and the selected
assignments govern. Historical SAD method sketches are not compiling public APIs.
No separate Legacy Contract Compatibility Matrix exists in this checkout. Existing
source contracts and `docs/engineering/compatibility-matrix.md` form the inspected
baseline; changes will preserve their signatures and enum values additively.

| Assignment | Source/test/evidence reconciliation before implementation |
| --- | --- |
| MOB-130-001 | No inventory/contract/qualification document or handoff existed |
| MOB-130-002 | Android implementation only; IOS enum alone is not driver support |
| MOB-130-003 | Android operations and MobileScreen marker exist; no Apple operations/context tests |
| MOB-130-004 | Core lazy registry/lifecycle exists; no Apple topology/isolation implementation |
| SEC-130-001 | Existing secret/artifact contracts exist; no Apple transport/security fixtures |
| MOB-130-005 | Android evidence exists; no Apple conditional-artifact implementation |
| SCF-130-001 | Mobile starter exists; blueprint explicitly rejects IOS |
| MCP-130-001 | Existing transports/jobs exist; blocked handoff only; no Apple qualification |

No assignment-specific review findings or prerequisite completion reports were found.
Reuse the Android/core implementation; do not count a planned capability as implemented.
The existing 1.2.0 source baseline satisfies this assignment's inventory dependency.
`docs/assignments/MOB-003-handoff.md` supplies historical real Android qualification
and cleanup evidence, not Apple evidence or a claim that those runs were repeated.

## Concrete inventory

All paths below are relative to the repository root.

| Contract or behavior | Actual path and observation |
| --- | --- |
| Mobile API | `codinglair-taf-runtime/taf-mobile-core/src/main/java/com/codinglair/taf/mobile/MobileController.java`: generic driver/element, native lifecycle/interaction/evidence; no obsolete MobileElement |
| Descriptors/providers | Same directory: `DeviceDescriptor.java`, `DeviceProvider.java`, `AppiumServerProvider.java`; provision/release remains provider-owned; descriptor has IOS but no family/mode/Apple readiness |
| Screen/task boundary | Same directory: `MobileScreen.java`; platform marker, no global screen/element cache |
| Android contracts/config | `codinglair-taf-runtime/taf-mobile-appium/src/main/java/com/codinglair/taf/mobile/appium/`: `AndroidController.java`, `AndroidControllerSettings.java`, `AndroidProperties.java`; retain `taf.mobile.android` and public signatures |
| Android strategy | Same directory: `AppiumAndroidSessionFactory.java`: AndroidDriver/UiAutomator2Options; preserve endpoint URI and existing defaults |
| Auto-configuration | Same directory: `AndroidAutoConfiguration.java`; enabled-only configurer registers lazy named controllers, aggregated ConsumerPreflightContributor |
| Lifecycle | `codinglair-taf-runtime/codinglair-taf-runtime-core/src/main/java/com/codinglair/taf/runtime/core/TestSession.java` and `controller/ControllerRegistry.java`; typed names, lazy get, cleanup of acquired controllers |
| Evidence | Core `reporting/ArtifactCollector.java`, `reporting/abstraction/TestArtifact.java`; Android `DefaultAndroidController.java` collects through context, redacts text, requires explicit visual policy |
| Runners | `codinglair-taf-runtime/codinglair-taf-runner-testng` and `codinglair-taf-runner-cucumber`; keep independent lifecycle integrations |
| Starter/consumer | `codinglair-taf-starter-mobile/pom.xml`; `release/consumer-smoke/starter-mobile/src/test/java/com/codinglair/taf/conformance/MobileStarterConformanceTest.java`; existing standalone Android context test |
| Manifest/generator | `docs/reference/starter-capability-manifest-v1.json`; `taf-mcp-server/taf-mcp-tools/src/main/java/com/codinglair/taf/mcp/tools/CapabilityContributionCatalog.java`, `BlueprintCompositionEngine.java`; IOS rejected by SCF_UNSUPPORTED_IOS until SCF-130-001 |
| Android tests | Appium `src/test/java/com/codinglair/taf/mobile/appium/AndroidControllerSettingsTest.java`, `AndroidAutoConfigurationTest.java`, `AndroidControllerLifecycleTest.java`, `AndroidEmulatorSmokeTest.java`; core DeviceDescriptorTest/MobileArchitectureBoundaryTest |
| Android infrastructure | `containers/android-emulator/google/verify.ps1`, `verify-compose.ps1`, `qualify.ps1`; `.github/workflows/mob-003-qualification.yml` has manual/reusable qualification, controlled failure cleanup and real smoke |
| Existing limitations | `docs/reference/android-appium-setup.md`, Appium README, `docs/architecture/decisions/MOB-001-appium-java25-compatibility.md`; Android native scope, opt-in device qualification; no inherited Apple certification |

Android's current lifecycle fixtures inject a failing session factory; they do
not emulate an Appium HTTP server. Its real smoke probes `/status`, opens a
UiAutomator2 session, checks health/source and closes in `finally`. Settings
fixtures cover endpoint credentials, package requirements, positive timeout
and visual authorization. Context fixtures cover disabled, lazy registration
and aggregated invalid configuration. Apple protocol fixtures must therefore
be newly implemented using JDK HTTP facilities, adapting those test boundaries.
Existing `DefaultAndroidController.collectArtifacts` and cleanup are the evidence
hooks to inspect; the plan does not assert complete Android artifact test coverage.

## Frozen additive configuration contract

Leave Android binding and default generation untouched. Add `taf.mobile.apple` in
the existing Appium module with enabled=false. Named instances inherit base settings;
explicit instance values override base values; authorized explicit job overrides are
last. Validate the final result, without mutating shared settings. Null means inherit;
false is an explicit boolean override. Extension duplicate ownership is an error,
not an alternate channel to override typed fields.

| Logical field | Chosen contract |
| --- | --- |
| Platform/family | ios -> IPHONE, ipados -> IPAD; apple requires explicit family; wire platform iOS, automation XCUITest; unsupported aliases/conflicts rejected |
| Mode/kind | NATIVE/HYBRID/SAFARI; SIMULATOR/PHYSICAL; no tvOS/watchOS/macOS |
| Endpoint/topology | HTTP(S) exact base URI, LOCAL_HOST/REMOTE_HOST/PROVIDER; no userinfo, fragment or credential query; remote JVM requires no local Xcode |
| Target/OS | device-name and configurable platform-version; device-id or explicit provider selection for physical; no invented business OS/device minimum |
| Application | PACKAGED or PREINSTALLED for native/hybrid; app reference kind SERVER_PATH/AUTHORIZED_URL/PROVIDER_UPLOAD with simulator/physical build declaration; bundle-id for preinstalled; Safari forbids install/bundle options; never reinterpret client Path as a remote file |
| Provider/auth | Namespaced nested JSON values, recursively copied; existing secret references, resolution only at authorized transport boundary; plaintext credential fields fail closed |
| WDA/isolation | Typed owned WDA/MJPEG ports, derived-data path, provider allocation metadata; no session override/global deletion or hidden signing/provisioning |
| Time/policy | Positive bounded command/context/cleanup durations; explicit REUSE/RELAUNCH/REINSTALL; screenshot/source/log/video requests and explicit visual policy; unsupported video separate from readiness |

Add Apple-specific contracts and implementation in `configuration`, `platform`,
`service`, `provider`, `evidence`, `exception` packages inside taf-mobile-appium.
Technology-neutral manifest/selection values belong in taf-mobile-core; it must
remain free of Appium/Selenium/Spring dependencies. No second starter or copied runner.

## Operation support and evidence owners

### Binding names, types and defaults

The following names freeze the planned Apple binding under `taf.mobile.apple`
and `taf.mobile.apple.controllers.<name>`. They are not yet implemented properties.
An empty controller map registers `default`; a nonempty map registers only its
named entries. Apple and Android have separate typed registrations in one session.
Android `AndroidProperties.settings(name)` currently selects the entire named
object instead of merging it: Apple inheritance must not change that behavior.

| Property | Type / default | Validation or mapping |
| --- | --- | --- |
| enabled | boolean / false | Disabled binding never opens a session |
| platform | string / apple | ios and ipados aliases infer family; apple requires device-family |
| device-family | IPHONE or IPAD / unset | Conflicting alias/family rejected; MobilePlatform.IOS retained |
| execution-mode | NATIVE, HYBRID, SAFARI / NATIVE | Separate from existing ApplicationMode |
| device-kind | SIMULATOR or PHYSICAL / unset | Required; do not rename existing DeviceMode values |
| topology | LOCAL_HOST, REMOTE_HOST, PROVIDER / REMOTE_HOST | Describes Appium host, not JVM operating system |
| server-url | URI / unset | Required exact authorized base URI; no path rewriting |
| device-name, device-id, platform-version | strings / unset | Name required; physical ID or provider-selection required; OS configurable |
| provider-selection | nested JSON object / empty | Authorized provider target selection, not provisioning |
| application-mode | PACKAGED or PREINSTALLED / PREINSTALLED | Applies only to native/hybrid |
| app-reference.kind, app-reference.value | enum and string / unset | SERVER_PATH, AUTHORIZED_URL, PROVIDER_UPLOAD; required for PACKAGED |
| app-reference.build-kind | SIMULATOR or PHYSICAL / unset | Required for package; match device-kind, not merely filename extension |
| bundle-id | string / unset | Required for PREINSTALLED; optional package identity check |
| authentication.mechanism | NONE, HEADER, BASIC, PROVIDER_CAPABILITY / NONE | Resolve through existing authorized secret/provider boundary |
| authentication.secret-references | map of opaque references / empty | Never credentials; HEADER/BASIC require corresponding reference entries |
| provider-options | namespaced nested JSON object / empty | Recursive copy, preserve types, reject duplicate typed capability ownership |
| wda.local-port, wda.mjpeg-port | integer / unset | 1â€“65535; allocation/collision ownership checked before session creation |
| wda.derived-data-path | server path string / unset | Owned per allocation; never infer client filesystem visibility |
| wda.build-mode | BUILD, PREBUILT, PREINSTALLED, RUNNING / BUILD | External readiness verifies prerequisites; no hidden provisioning |
| wda.signing-team-id, wda.signing-identity, wda.bundle-id | strings / unset | Optional external signing metadata; signing material stays outside configuration |
| wda.prebuilt-path, wda.base-url | server path, URI / unset | Required for corresponding PREBUILT/RUNNING mode; URI authorization applies |
| command-timeout, readiness-timeout, context-timeout, cleanup-timeout | Duration / 2m, 30s, 30s, 30s | Positive, at most 10m each; cancellation honored |
| lifecycle-policy | REUSE, RELAUNCH, REINSTALL / RELAUNCH | REINSTALL requires authorized packaged reference; no device erase |
| terminate-app-on-close, uninstall-packaged-app-on-close | boolean / true, false | Safari application cleanup options rejected when explicitly supplied |
| screenshot-on-failure, page-source-on-failure, device-logs, video | boolean / true, true, true, false | Conditional availability recorded separately per artifact |
| allow-visual-artifacts | boolean / false | Required for screenshots/video; request does not imply authorization |
| auto-accept-alerts, auto-dismiss-alerts | boolean / false, false | Mutually exclusive, explicit opt-in |

Safari emits `browserName=Safari`, `platformName=iOS`, and
`appium:automationName=XCUITest`; it rejects explicitly configured application-mode,
bundle-id, app-reference and app cleanup fields. Native/hybrid omit browserName.
Apple app-reference replaces the *new Apple* binding's client `Path` ambiguity;
the existing `MobileController.install(Path)` and Android binding are preserved.
Apple-specific reference installation is additive, and legacy Path installation
must document server-path semantics or reject an ambiguous remote use.
No arbitrary vendor-command MCP interface is planned.

### Operation cases

| Operation | Planned implementation/test owner | Conditional limitation |
| --- | --- | --- |
| Install/remove | MOB-130-003 IOSDriver application methods, protocol requests | Server-visible simulator build vs signed physical build; no hidden upload |
| Activate/query/terminate | MOB-130-003 bundle-ID application requests | Safari browser lifecycle separate |
| Reset/background | MOB-130-003 explicit policy and bounded duration tests | Reinstall does not promise Keychain/permissions erasure |
| Tap/type/find | MOB-130-003 native accessibility/predicate/class-chain vs web selectors | Resolve elements in current context |
| Swipe/long press | MOB-130-003 W3C actions request tests | Context/device gesture differences explicit |
| Orientation | MOB-130-003 supported orientation request/unsupported fixture | Device/application support conditional |
| Deep link | MOB-130-003 XCUITest mobile: deepLink request tests | Qualified driver/OS required |
| Permissions | MOB-130-003 simulator permission request; physical explicit unsupported/system dialog | No arbitrary physical grant emulation |
| Notifications/dialogs | MOB-130-003 platform UI screen/alert fixtures | Push production belongs to SUT; auto handling explicit |
| WebView/native return | MOB-130-003 deterministic clock/polling, explicit selection and stale-handle tests | App inspectability/Web Inspector prerequisites; no first/last selection |
| Android-only commands | MOB-130-003 unsupported-operation tests | No activity/intent/keycode/network/Logcat emulation |
| Evidence | MOB-130-005 per-artifact outcomes and ordering tests; SEC-130-001 canaries | Genuine device logs only; video conditional, bounded visual capture opt-in |

## Requirements and BRD acceptance ownership

| Requirement/acceptance point | Implementation owner | Verification owner and layer |
| --- | --- | --- |
| FR-MOB-011, FR-MOB-021; Â§33.4.1 | MOB-130-001/002, DOC-130-001 | VER-130-001 contracts; VER-130-002 exact compatibility records |
| FR-MOB-013, FR-MOB-014; Â§33.4.2 | MOB-130-002/003, SCF-130-001 | VER-130-001 generated native/hybrid/Safari consumer compile/context; VER-130-002 available live targets |
| Â§33.4.3 | This Android-derived plan, all implementation owners | VER-130-001/002 distinguish unit/protocol, standalone consumer and actual infrastructure |
| FR-MOB-012, FR-MOB-015, FR-MOB-025; Â§33.4.4 | MOB-130-002/004 | Protocol exact-path tests, local/remote readiness negative cases; live topology if available |
| FR-MOB-008, FR-MOB-019; Â§33.4.5 | MOB-130-005, SEC-130-001 | Artifact outcomes, failure-before-teardown, leak canaries; real artifacts when available |
| FR-MOB-023, FR-MOB-024; Â§33.4.6 | SCF-130-001, MCP-130-001 | Both transport discovery/validation/authorized execution/cancellation suites and immutable local image |
| FR-MOB-022, FR-MOB-025; Â§33.4.7 | MOB-130-004 | Two concurrent fixture sessions, collision rejection, cancellation/partial-init cleanup; external coordination documented |
| FR-MOB-009, FR-MOB-020, FR-MOB-026; Â§33.4.8 | MOB-130-002/003, SCF-130-001 | Existing Android regression + shared task with platform screens, no static state |
| FR-MOB-016; Â§33.4.9 | SEC-130-001 | Nested credential references, denied endpoint/override, redirect trust and sentinel leak tests |
| FR-MOB-017, FR-MOB-018 | MOB-130-003 | Every operation row above, bounded hybrid timeout/return/stale cases |
| FR-MOB-005, FR-MOB-006, FR-MOB-007 | MOB-130-003 | Native interaction, application lifecycle and platform dialog/gesture protocol fixtures; VER-130-001 regression |
| FR-MOB-010 | MOB-130-002/004, SEC-130-001 | Provider SPI ownership and external endpoint fixtures; no paid-provider certification |
| FR-MOB-026 | MOB-130-002/003/004/005, SEC-130-001 | VER-130-001 Android defaults/API/context/lifecycle/evidence regression and architecture gates |
| Â§33.4.10 | REL-130-001 readiness, VER-130-001/002 | Sole Apple scope audit, truthful release evidence/limitations; publication release-owner action |

Fixture selection: an in-process W3C HTTP fixture for deterministic IOSDriver
requests and cancellation; consumer-supplied lawful native/hybrid application
references for optional live checks; mobile Safari against a configurable test URL.
No app binary, paid provider, machine path, model, OS minimum or concurrency minimum
is prescribed. Family/kind/mode/topology parameterized fixtures cover both families,
both kinds, all three modes and local/remote/provider configuration. Live coverage
is restricted to actual supplied infrastructure, with each absent tuple unverified.

## Dependencies and service lock

Retain the already pinned Java client 10.1.1 and Selenium BOM 4.43.0, Apache-2.0.
No dependency addition/upgrade is requested. Checked on 2026-10-01, the [upstream client matrix](https://github.com/appium/java-client#compatibility-matrix)
lists this pair. The root BOM pins Selenium artifacts through selenium-bom; do not
resolve the client's open range independently. Main risk is binary drift; retain
Android tests and convergence/architecture gates on Java 25/Spring Boot 4.

The initial external-service candidate is locked to Appium **3.0.0**, XCUITest
**10.0.0**, Node **22.12.0**, npm **10.9.0**. This is a qualification candidate,
not a security approval, installed dependency, or tested service tuple.
[Appium 3.0.0 package](https://github.com/appium/appium/blob/appium%403.0.0/packages/appium/package.json)
and [XCUITest 10.0.0 package](https://github.com/appium/appium-xcuitest-driver/blob/v10.0.0/package.json)
declare compatible Node and npm engines and an Appium 3 peer boundary. XCUITest
depends on a ranged WDA package; record the actually resolved WDA version/hash
before live qualification, never infer an exact WDA version from that range.
Both service packages declare Apache-2.0; the Node/npm runtime and resolved
transitive packages require their own license/security inventory before live
qualification. Retaining Maven dependencies adds no new dependency exposure.
Selecting these external candidates authorizes neither installation nor a
security exception; SEC-130-001/VER-130-002 retain transport and qualification
responsibility. The 9.5.0/4.34.0 alternative is unnecessary because the existing
10.1.1/4.43.0 pair is listed and already pinned; no automatic 10.x adoption occurs.

This Windows JVM has Java 25; Node is absent. No prepared Apple endpoint, macOS,
Xcode, target OS/device, WDA/signing metadata or app fixture was supplied. These
are unverified infrastructure, not inventory blockers. Before any live run lock
the actual macOS/Xcode/WDA/device/app hash tuple, check upstream compatibility,
and reconcile service patches if the operator supplies a different tuple. No
latest version at execution and no invented available Apple toolchain.

## Local verification plan

Run Maven Wrapper through configured settings/mirrors. Save full output under
ignored `target/`; record exit status, skips and failures, not just compilation.

```text
.\mvnw.cmd -pl codinglair-taf-runtime/taf-mobile-appium -am verify
.\mvnw.cmd -pl codinglair-taf-runtime/taf-mobile-appium -am verify -Parchitecture,dependency-analysis
.\mvnw.cmd -pl taf-mcp-server/taf-mcp-tools -am verify
.\mvnw.cmd -pl taf-mcp-server/taf-mcp-transport-stdio -am verify
.\mvnw.cmd -pl taf-mcp-server/taf-mcp-transport-http -am verify
.\mvnw.cmd -Papi-compatibility,schema-compatibility verify
```

The standalone mobile starter consumer is verified from `release/consumer-smoke/starter-mobile/pom.xml`
after local candidate artifacts are installed; it must not inherit reactor/test
fixtures. Existing Android `-Pandroid-emulator`/`-Pandroid-device` runs require the
documented TAF_ANDROID_* settings and retain explicit smoke skip reporting.
Reuse Android manual/nightly/release cadence for optional real Apple checks.
Image qualification records an immutable local digest, source identity, profile,
catalog/transport/security outputs; public push/master SHA are unnecessary.

Review each assignment separately against its handoff and exact scoped diff.
Architectural review covers additive API, provider ownership, dependency direction,
passive context/discovery and lifecycle isolation. Pending review is separate
from implementation completion; an unmet criterion reopens its owning assignment.

## Qualification case identifiers and record format

VER-130-001 owns deterministic cases A01â€“A08; VER-130-002 owns optional live
execution and exact tuple records for the same behaviors. DOC-130-001 publishes
their limitations, and REL-130-001 audits coverage without inventing execution.

| Case | Required planned coverage |
| --- | --- |
| A01 selection | Cartesian configuration/protocol cases: 2 families Ã— 2 kinds Ã— 3 modes Ã— 3 topologies; alias conflicts, Safari/app conflicts, Android unchanged |
| A02 options | Base/named/job precedence, explicit false, nested JSON copy, reserved capability collisions, package location/build kind and exact URI paths |
| A03 operations | Every operation-support row: successful request, invalid input, driver rejection, supported/unsupported conditional behavior |
| A04 context | Delayed/multiple/missing WebViews, explicit selection, timeout/cancellation, native return and invalidated element handles |
| A05 readiness | Disabled/passive discovery creates no driver; unreachable server, missing local tools, remote JVM without Xcode, target/WDA mismatch, optional video unavailable |
| A06 isolation | Two concurrent fixture sessions with independent device leases, WDA/MJPEG ports, derived data and artifact namespaces; collisions rejected; close/failure/cancel/partial-init release only owned resources |
| A07 evidence/security | Failure capture before teardown, individual artifact availability, bounded capture, visual opt-in, nested secret canaries, endpoint/redirect/override denial |
| A08 consumer/regression | Standalone native/hybrid/Safari generated consumers, independent TestNG/Cucumber integration, shared task/platform screens, both MCP transports, retained Android API/defaults/smoke |

For each executed case retain: case ID and owner; source HEAD plus scoped diff
hash; Java/client/Selenium/service versions; evidence layer (unit/protocol,
standalone consumer, real infrastructure); family/kind/mode/topology; sanitized
endpoint identity; actual macOS/Xcode/SDK/OS/device/WDA/build/signing readiness;
application or URL fixture identity/hash; exact command/profile; timestamps,
exit code, tests/failures/errors/skips; artifact availability; cleanup result;
evidence paths and SHA-256. Never include credentials or private signing data.
Use PASSED, FAILED, UNSUPPORTED or UNVERIFIED per configuration/operation;
a successful mock request is not a live-device result. External multi-process
allocation requires the operator/provider's shared lease coordination, not
merely a JVM-local lock. No live tuple is verified by this assignment.

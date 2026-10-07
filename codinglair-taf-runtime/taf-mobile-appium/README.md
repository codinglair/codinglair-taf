# TAF Mobile Appium

Native Android automation through an invocation-owned Appium UiAutomator2 controller. The module connects only to an existing Appium endpoint and provisioned device; it does not start Appium, create emulators, or authorize devices.

## Local emulator profile

```yaml
taf:
  mobile:
    android:
      enabled: true
      server-url: http://127.0.0.1:4723
      device-name: taf-api35
      device-mode: local-emulator
      application-mode: preinstalled
      app-package: com.example.app
      app-activity: .MainActivity
      allow-visual-artifacts: true
```

Run the opt-in smoke with `./mvnw -pl codinglair-taf-runtime/taf-mobile-appium -am verify -Pandroid-emulator`. The environment must provide the Appium 2 server, UiAutomator2 driver, an already-running emulator, and `TAF_ANDROID_APPIUM_URL`, `TAF_ANDROID_DEVICE_NAME`, `TAF_ANDROID_APP_PACKAGE`, plus optional `TAF_ANDROID_DEVICE_ID` and `TAF_ANDROID_APP_ACTIVITY` settings. MOB-002 provides the reproducible local/CI stack and complete smoke command under `containers/android-emulator`.

## Authorized physical-device profile

Physical-device execution is deliberately opt-in. Connect and explicitly authorize a dedicated non-production Android device, confirm it with `adb devices`, set its serial as `device-id`, select `authorized-physical-device`, and run `-Pandroid-device`. Do not use personal/production devices or embed credentials. Device authorization and provisioning remain infrastructure responsibilities.

Packaged applications installed through controller operations are removed on close by default. Preinstalled applications are never uninstalled. Both modes terminate the declared package on close unless configured otherwise. Screenshots/video require explicit visual-artifact authorization.

## Apple session configuration (MOB-130-002)

The same mobile starter includes disabled-by-default `taf.mobile.apple` configuration
and lazy, typed `AppleController` registrations. Creating a Spring context or a
TestSession does not connect to Appium. Obtaining the controller through the registry
initializes its IOSDriver with XCUITestOptions. Android properties, defaults,
signatures and named settings selection are preserved.

```yaml
taf:
  mobile:
    apple:
      enabled: true
      platform: ios
      device-kind: simulator
      server-url: http://prepared-host.example.test:4723/custom/wd/hub
      device-name: customer-prepared-simulator
      bundle-id: com.example.customerapp
      controllers:
        phone:
          device-name: customer-phone-simulator
        tablet:
          platform: ipados
          device-name: customer-tablet-simulator
```

`ios` implies IPHONE; `ipados` implies IPAD; `apple` requires `device-family`.
The wire platform is always `iOS`; automation is XCUITest. `device-kind` is
SIMULATOR or PHYSICAL. Physical targets require `device-id` or namespaced
`provider-selection` capabilities with PROVIDER topology. Topology defaults to
REMOTE_HOST; LOCAL_HOST and PROVIDER describe external infrastructure ownership.
No local Xcode, device provisioning, signing or app upload occurs in this module.

Execution modes are NATIVE (default), HYBRID and SAFARI. Native/hybrid default to
PREINSTALLED application mode and require `bundle-id`. PACKAGED requires an
`app-reference` with `kind`, `value` and matching `build-kind` (SIMULATOR/PHYSICAL).
Kinds are SERVER_PATH (absolute Apple server path), AUTHORIZED_URL (HTTP(S)
reference already authorized by the caller), and PROVIDER_UPLOAD (an existing
provider reference, PROVIDER topology only). Client filesystem paths are never
converted or uploaded. Build-kind is an explicit declaration, not binary inspection.
Safari emits `browserName=Safari`; omit application-mode, bundle-id, app-reference
and application cleanup properties, including explicit false values. Hybrid sessions may supply
`additional-webview-bundle-ids`, a list of additional application identifiers reported by Web
Inspector. The list maps to XCUITest's `appium:additionalWebviewBundleIds` capability and is
rejected outside HYBRID mode.

An empty controller map registers `default`; a nonempty map registers only its
named entries. Apple named settings inherit base/profile binding; explicitly
supplied instance values override base; already authorized explicit job values
passed to `AppleControllerSettings.resolve` are last. Null inherits; false overrides.
The merge API does not authorize a job or endpoint. Results are detached copies.
Provider maps retain nested JSON types, are bounded and immutable after copying,
and reject conflicting duplicate ownership and reserved Appium/W3C fields.
Providers use namespaced capabilities such as `cloud:options`; authentication
values cannot be supplied in these maps.

The exact server URI path is preserved. Endpoint/app URLs cannot contain userinfo,
query strings or fragments. Authentication binds NONE, HEADER, BASIC or
PROVIDER_CAPABILITY plus opaque `authentication.secret-references`. Authenticated
transport uses the existing SecretManager only after resource checks; absent a
manager it fails closed. BASIC requires username/password
references. Existing references use `secret://env/NAME`, `secret://jasypt/...` or
`credential://profile-alias` syntax.

WDA fields include local-port, mjpeg-port (distinct 1–65535), derived-data-path,
build-mode (BUILD/PREBUILT/PREINSTALLED/RUNNING), signing-team-id, signing-identity,
bundle-id, prebuilt-path and base-url. Paths refer to the Apple server. External
allocation/readiness and collision checks are described below; no signing
material is accepted. Command timeout defaults to 2m; readiness/context/cleanup
timeouts default to 30s; all must be positive and at most 10m. Command timeout is
sent as Appium newCommandTimeout; context-timeout bounds WebView polling.
HTTP connection and read deadlines are bounded by the configured readiness,
cleanup and command durations (the read deadline uses their minimum).

Lifecycle policy binds REUSE/RELAUNCH/REINSTALL (default RELAUNCH); REINSTALL
requires PACKAGED. Application cleanup flags default true/false for terminate/
uninstall. Evidence defaults are screenshot/source/logs true, video false, visual
authorization false. Alert accept/dismiss default false and are mutually exclusive.
Apple operations, explicit reset policy, owned cleanup, context-bound elements,
hybrid selection and Safari navigation are documented in the
[operation and verification table](../../docs/reference/apple-appium-operations.md).
Conditional visual/source/log/recording evidence remains MOB-130-005.
Real-device qualification is not claimed. See the
[contract and qualification plan](../../docs/engineering/apple-130-contract-and-qualification-plan.md).

### Topology readiness and allocation ownership

`AppleReadiness.inspect(settings)` returns the existing `HealthResult` contract.
Passive discovery/preflight never opens a connection, allocates a device or runs
host commands. `prerequisites` accepts only boolean operator/provider declarations
for `endpoint`, `compatibility`, `target`, `application`, `wda`, `doctor`, `xcode`,
`device`, and `signing`. Supply current sanitized results rather than doctor output,
signing material or credentials. False yields actionable unavailable diagnostics;
missing prerequisites remain unknown. LOCAL_HOST additionally consumes the four
host prerequisites; REMOTE_HOST/PROVIDER never require Xcode on the Java worker.
A positive endpoint declaration or `/status` response cannot prove readiness.
Successful authorized TestSession initialization provides final session confirmation.
Optional video availability is separate from readiness. Authentication availability
is confirmed only during authorized initialization; HTTPS uses the JVM trust boundary.
See [Apple transport security](../../docs/reference/apple-transport-security.md)
for nested references, trusted standalone resources, governed policies, redirects,
failure handling, content suppression and compatibility.

An optional `allocation-resource` names an already authorized session-specific
`EnvironmentAccess.Resource` with type `apple-session-allocation`. Its provider
must reserve the target and service resources before exposing it. Properties are
`exclusive=true`, required `device-id`, optional `wda-port`, `mjpeg-port`, and
`derived-data-path`. These override the detached controller snapshot only. The
provider/TestSession retains allocation cleanup ownership; the controller neither
provisions infrastructure nor claims/reclaims a provider's pool. Explicit IDs and
WDA settings without a resource must likewise come from external reservations or
expressly delegated owned ranges; this module does not allocate arbitrary ports.

The default Spring factory shares an `AppleResourceReservations` coordinator across
its controllers and TestSessions. It rejects observable duplicate target IDs,
WDA/MJPEG host ports, derived-data paths and running WDA URLs before connection.
Standalone users share a coordinator through the additive four-argument controller
constructor. The existing three-argument constructor remains supported with its
own coordinator. Device names/opaque provider selections, host aliases, independent
factories/JVMs and workers cannot establish global exclusivity: coordinate through
the host/provider. Do not enable Appium session-override on shared hosts.

Cancellation preserves interruption. Session creation runs once, without automatic
retry/replacement. Transport failure during creation reports an uncertain remote
outcome. An optional provider `owned-session-id` (letters, digits, `_`, `-`, maximum
128 characters) permits a bounded DELETE of exactly that owned session using the
authorized endpoint. No enumeration/global deletion is performed. A pending create
may finish after DELETE/404, so uncertain reservations remain quarantined even
after that best-effort cleanup. The provider/operator must reconcile the allocation
before reusing it; do not recreate the coordinator to bypass quarantine. Failed quit
also retains the reservation. Definite rejected initialization releases it.
Quarantine lasts for the coordinator's lifetime. After authoritative external
reconciliation, a fresh factory/coordinator may resume use of the allocation.

Close gathers sanitized context/version metadata, conditional device/system logs
and final recording through the session's ArtifactCollector before application
cleanup and driver quit. Failure/explicit collection also requests configured
screenshots and page source under the existing security policy. See
[Apple evidence and reporting](../../docs/reference/apple-evidence-and-reporting.md)
for outcomes, bounds, provider trust and optional `require-evidence` strictness.
Cleanup is idempotent, attempts quit after app cleanup failure, and releases only
definite completed reservations. Shared runner lifecycle attaches cleanup failures
to the primary test failure. Each HTTP exchange is bounded; application cleanup
may require several exchanges within these individual limits.

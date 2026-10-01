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
and application cleanup properties, including explicit false values.

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
PROVIDER_CAPABILITY plus opaque `authentication.secret-references`. Non-NONE
session creation fails closed until SEC-130-001 supplies authorized transport;
references are never resolved by this assignment. BASIC requires username/password
references. Existing references use `secret://env/NAME`, `secret://jasypt/...` or
`credential://profile-alias` syntax.

WDA fields include local-port, mjpeg-port (distinct 1–65535), derived-data-path,
build-mode (BUILD/PREBUILT/PREINSTALLED/RUNNING), signing-team-id, signing-identity,
bundle-id, prebuilt-path and base-url. Paths refer to the Apple server. External
allocation/readiness and collision checks belong to MOB-130-004; no signing
material is accepted. Command timeout defaults to 2m; readiness/context/cleanup
timeouts default to 30s; all must be positive and at most 10m. Command timeout is
sent as Appium newCommandTimeout; context-timeout bounds WebView polling.
Readiness/cleanup transport deadlines remain subsequent assignment responsibilities.

Lifecycle policy binds REUSE/RELAUNCH/REINSTALL (default RELAUNCH); REINSTALL
requires PACKAGED. Application cleanup flags default true/false for terminate/
uninstall. Evidence defaults are screenshot/source/logs true, video false, visual
authorization false. Alert accept/dismiss default false and are mutually exclusive.
Apple operations, explicit reset policy, owned cleanup, context-bound elements,
hybrid selection and Safari navigation are documented in the
[operation and verification table](../../docs/reference/apple-appium-operations.md).
Readiness/isolation remain MOB-130-004; conditional evidence remains MOB-130-005.
Real-device qualification is not claimed. See the
[contract and qualification plan](../../docs/engineering/apple-130-contract-and-qualification-plan.md).

# Android and Appium setup and troubleshooting

## Supported boundary

The initial mobile capability is native Android through Appium and UiAutomator2, using an approved
local emulator or authorized physical device. iOS/XCUITest, hybrid/mobile web, Appium Grid, and
broad device-cloud delivery remain deferred behind provider boundaries.

## Workstation setup

1. Install an approved Java 25 JDK and Android SDK/platform tools. Set the SDK location using the
   organization-approved workstation mechanism; do not commit machine paths.
2. Obtain an Android emulator image approved under the
   [emulator image policy](../operations/android-emulator-image-approval-policy.md), or enable USB
   debugging on an authorized test device.
3. Verify exactly the intended target is visible with `adb devices`. Accept device authorization
   only on the controlled test device and revoke stale host authorizations.
4. Install the approved Appium 3 release and UiAutomator2 driver through the organization package
   source. Start Appium on a loopback or protected endpoint without embedded credentials.
5. Use package mode for an approved APK path or preinstalled mode for an already installed package.

Example Spring configuration uses a logical name and contains no secret:

```yaml
taf:
  mobile:
    android:
      enabled: true
      controllers:
        primary:
          server-url: http://127.0.0.1:4723
          device-name: approved-api35-emulator
          device-mode: local-emulator
          application-mode: preinstalled
          app-package: com.example.app
          app-activity: .MainActivity
          command-timeout: 2m
          screenshot-on-failure: true
          page-source-on-failure: true
          device-logs: true
          video: false
```

Physical-device certificates, signing keys, MDM enrollment, and USB policy are operator-owned.
Store only references to credentials or profiles; never copy private keys into TAF configuration.

## Troubleshooting and evidence

- No device: inspect `adb devices`, authorization state, cable/USB policy, or emulator boot
  completion. Reject ambiguous multiple-device selection; configure the intended device ID.
- Session creation failure: confirm Appium endpoint, UiAutomator2 driver, package/activity, Android
  compatibility, and that another session does not own the device.
- Packaged app failure: verify the approved APK exists and is compatible; cleanup may uninstall only
  the invocation-owned packaged app.
- Timeout: inspect bounded Appium/device logs and preserve cancellation/interruption; do not add
  arbitrary sleeps.
- Evidence: screenshots, page source, device logs, and video flow through `ArtifactCollector`.
  Visual capture requires policy approval; enable video only with `allow-visual-artifacts=true`.
  Sanitize notifications, user data, tokens, and device identifiers before persistence.


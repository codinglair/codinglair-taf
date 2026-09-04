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

Cloud providers and iOS/XCUITest implement the mobile-core provider/controller boundaries; neither is implemented by MOB-001.

# Apple Appium operations and contexts

Apple operations use the existing mobile starter and the named `AppleController`
registration. Android contracts and defaults are unchanged. The consumer execution
boundary must authorize the configured target, app reference and operations.
Controllers consume an existing Appium/XCUITest service; they never provision or
erase devices. API additions are in `taf-mobile-appium`, without new dependencies.

## Operation and verification table

All implementation links below refer to
[DefaultAppleController](../../codinglair-taf-runtime/taf-mobile-appium/src/main/java/com/codinglair/taf/mobile/appium/service/DefaultAppleController.java).
Tests use the real pinned Java client against a local W3C HTTP fixture, not a real
Apple device. Method names identify the checks in
[AppleOperationsProtocolTest](../../codinglair-taf-runtime/taf-mobile-appium/src/test/java/com/codinglair/taf/mobile/appium/AppleOperationsProtocolTest.java).

| Operation / implementation method | Protocol or behavior check | Conditional limitation |
| --- | --- | --- |
| `install`, `uninstall` | `install`, `reset` | Explicit install uses only the validated configured server/authorized URL/provider reference. No JVM file upload. Requires PACKAGED mode and a bundle ID for cleanup; optional package bundle identity must be supplied when requesting identity-dependent operations. Declared build kind must match target kind; binary/signature validity is server-owned. Explicit uninstall requires bundle ID and is a caller-authorized mutation. |
| `launch`, `activate`, `queryAppState`, `terminate` | `operations` | Bundle ID required; native/hybrid only. Launch uses `mobile: launchApp`; activate/query/terminate use pinned IOSDriver methods. App must be installed. |
| `reset` | `reset` (all policies) | Explicit invocation applies configured policy: REUSE sends no mutation; RELAUNCH terminates/activates; REINSTALL removes/installs/activates. No automatic action replay, Keychain clearing, permission reset or device erase. Initialization requests `noReset=true`, leaving destructive resets to explicit operations. Appium may install/launch the declared app during session creation. |
| `close` | `cleanupFailure`, `reset`; AppleSessionProtocolTest `closeRace`, `verticalSession` | Terminate only configured app (default true), remove only a successfully explicitly installed app when opted in (default false), always attempt session quit after cleanup failure. Server session-creation installation alone does not grant removal ownership. Repeated close is idempotent. |
| `background` | `operations`, `duration` | Explicit `mobile: backgroundApp` duration from 1 ms to 10 minutes. Appium/OS timing precision varies; indefinite backgrounding is rejected. |
| `find`, `tap`, `type`, `text` | `operations`, `hybrid`, `failures`, `isolation`, `invalidInputs` | Immutable locator specification; resolve in current context. Accessibility/predicate/class-chain native only; CSS web only; XPath context-dependent. Remote stale handles fail without retry. |
| `swipe`, `longPress` | `operations`, `duration`, `invalidInputs` | W3C touch actions; nonnegative viewport coordinates and bounded duration. Caller supplies coordinates within the target viewport; app/device may reject gestures. |
| `setOrientation` | `operations` | W3C orientation; app/device must support requested orientation. Server rejection becomes sanitized operation failure. |
| `openDeepLink` | `operations`, `invalidInputs` | Absolute URI without userinfo and configured bundle ID. XCUITest 4.17+, Xcode 14.3+, iOS 16.4+ required by the platform API; simulator and physical target must support the URL/app association. No fallback emulation. |
| `grantPermission`, `revokePermission` | `operations`, `physical` | `mobile: setPermission` is simulator-only. Server may require applesimutils; location permissions use simctl with qualified driver support and can terminate the app. Physical-device permission tests use system dialogs/screens. Unsupported permission names or services fail through the server. |
| `acceptDialog`, `dismissDialog` | `operations` | Supported WebDriver alert APIs. Custom dialogs require app screens. Automatic accept/dismiss remains an explicit, mutually exclusive configuration option, false by default; it can invalidate permission tests. |
| `openNotifications` | `operations` | Native-context top-center downward W3C gesture for available Notification Center UI on an unlocked authorized target. OS/device/orientation may require a different screen gesture; verify UI with a target-specific screen. No guarantee of opening UI on every device. SUT/infrastructure owns notification production and push delivery. |
| `contexts`, `selectWebView`, `returnToNative` | `hybrid`, `lateWebView`, `timeout`, `failedSwitch`, `isolation` | HYBRID explicit exact identifier, never first/last selection; bounded 50 ms polling with configured context-timeout. Native return uses NATIVE_APP. Switch attempts invalidate handles even if remote result is uncertain. |
| `navigate` | `safari` | Safari or explicitly selected WebView only. XCUITest mobile Safari is separate from desktop Safari and Playwright WebKit. Native app lifecycle/install and hybrid discovery are rejected in Safari mode. |
| `androidOperation` | `android` (all enum values) | Activity, intent, key-code, network and Logcat extensions fail explicitly on Apple before connection. No Android-equivalent semantics are fabricated. |

Platform API details: [XCUITest execute methods](https://appium.github.io/appium-xcuitest-driver/latest/reference/execute-methods/)
and [hybrid prerequisites](https://appium.github.io/appium-xcuitest-driver/latest/guides/hybrid/).

## Context and session ownership

Use `contexts()` to inspect available identifiers, then call
`selectWebView("WEBVIEW_<explicit app identifier>")`. Discovery does not change
context until that exact identifier appears. A missing expected view reports
WKWebView inspectability, Safari Web Inspector and device/host authorization
requirements without returning raw provider payloads. Enable inspectability in
the customer test build and Web Inspector where required; TAF does not alter apps.
Use `returnToNative()` for explicit recovery.

`AppleElement` is opaque and bound to one controller and context generation.
Re-resolve after context switches, navigation or application state operations;
returning to the same native context does not revive old elements. A stale remote
element is never retried automatically. Controller operations and close are
serialized per instance. Interruption stops polling and remains set. Context
timeout bounds polling; it does not impose a hard deadline on an in-flight HTTP
request. Transport cancellation/timeouts and cross-client target allocation are
MOB-130-004 responsibilities. `newCommandTimeout` is server session idle timeout.

`nativeDriver()` remains a consumer escape hatch. Direct vendor calls bypass
handle invalidation, validation and reporting; consumers must discard handles
after direct context/navigation mutations and own their vendor-specific safety.
Raw vendor payloads are not exposed by normal operation failures. No new MCP
command surface, Allure coupling or evidence collector is introduced here.

## Shared business tasks

[SharedCheckoutTaskTest](../../codinglair-taf-runtime/taf-mobile-appium/src/test/java/com/codinglair/taf/mobile/appium/SharedCheckoutTaskTest.java)
is a compiled representative composition. `SubmitOrder` depends on the business
`CheckoutScreen` contract; composition selects Apple/Android native screens by
platform, or injects a web screen explicitly. Each screen owns its named immutable
selector and session-local controller/driver; elements are resolved per action.
The same task executes across the three screens with distinct platform selectors.
No application-specific checkout API or fixture is published in Runtime. Real
consumer examples and scaffold integration remain SCF-130-001; complete test reuse
is not required.

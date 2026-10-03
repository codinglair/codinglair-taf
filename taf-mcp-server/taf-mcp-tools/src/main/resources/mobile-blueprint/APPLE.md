# Apple consumer

This standalone Java 25 project imports the TAF BOM and existing mobile starter.
`mvn test` compiles all examples and runs passive context checks without Apple host access.
Run authorized interactions with `mvn test -Dsurefire.suiteXmlFiles=src/test/resources/testng-apple.xml`.
When Cucumber is selected, run `mvn test -Dtest=AppleBehaviorRunner` separately.
Both paths use framework session lifecycle and consolidated `ConsumerPreflight`.
Missing app identity, target, operator confirmations and interaction data fail together before driver use.

Configure `capabilities/mobile.yml` or environment variables: `APPLE_SERVER_URL`,
`APPLE_DEVICE_NAME`, `APPLE_DEVICE_ID`, `APPLE_BUNDLE_ID`, `APPLE_ACCESSIBILITY_ID`,
`APPLE_EXPECTED_TEXT`, `APPLE_WEBVIEW` (exact identifier), `APPLE_TEST_URL`, `APPLE_CSS_SELECTOR`.
Use an authorized consumer application and selectors for its known interaction.
Native taps and asserts the resulting text. Hybrid selects the exact WebView, asserts text
and returns to native even after an assertion/read failure. Safari navigates the configured URL
and asserts the selected DOM text. No application binaries or unlicensed fixtures are included.

Use IOS/IPHONE or IPADOS/IPAD; XCUITest always sends iOS on the wire. SIMULATOR and
PHYSICAL require matching builds; physical targets need an authorized device-id or provider allocation.
LOCAL_HOST assumes an equipped Apple host; REMOTE_HOST and PROVIDER use external infrastructure and
do not require Xcode on this client. Configure WDA/signing/ports/derived-data and readiness
`prerequisites` only from verified operator/provider declarations. Generation is not qualification.
See the Runtime Apple configuration reference for the required topology-specific confirmations.

Preinstalled native/hybrid uses `bundle-id` and the default PREINSTALLED application mode.
For a packaged consumer app, set `application-mode: PACKAGED` and a typed `app-reference`:

```yaml
app-reference:
  kind: SERVER_PATH
  value: /authorized/apps/Consumer.app
  build-kind: SIMULATOR
```

The path belongs to the Apple Appium server. AUTHORIZED_URL and PROVIDER_UPLOAD are supported
when permitted by the trusted execution policy; use PHYSICAL build-kind for a signed physical build.
Safari forbids app-reference, bundle-id, application-mode and application cleanup options.
Remote authentication and nested provider options contain secret references only;
resolve them through the existing authorized secrets provider. Never put credentials in endpoints.
The optional taf-local profile must be explicitly selected; it activates the local secret provider.
No provisioning, signing, static drivers or copied runner lifecycle is supplied.

The supported direct-module alternative imports the same BOM and uses public `taf-mobile-appium`,
`codinglair-taf-runtime-core` and `codinglair-taf-runner-testng` artifacts instead of the starter;
add existing reporting/secrets/environment modules only as needed. Cucumber remains independent.

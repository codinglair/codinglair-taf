# Apple Appium consumer guide for TAF 1.3.0

This guide describes the public Apple capability targeted for Codinglair TAF 1.3.0.

TAF supports iPhone/iOS and iPad/iPadOS, native and hybrid applications, mobile Safari,
simulators and authorized physical devices through Appium/XCUITest. TAF consumes prepared
infrastructure. It does not install Xcode/Appium, create or erase devices, accept licenses,
manage signing profiles, build WebDriverAgent (WDA), or provision a device cloud.

## Quick start

The executable source for every snippet in this guide is the standalone
[Apple consumer project](../../examples/apple-appium-consumer/README.md). It has no reactor
parent or internal test-fixture dependency. From the repository root, first install the
candidate artifacts, then verify the consumer without opening a driver:

Repository-local verification uses the candidate version declared by the root Maven POM.
Published consumers must use the released <!-- taf-version -->`1.2.0` BOM and mobile starter. Java 25 and the
checked-in Maven Wrapper are required.
```powershell
.\mvnw.cmd -pl codinglair-taf-starter-mobile,codinglair-taf-runtime/codinglair-taf-runner-cucumber -am install
.\mvnw.cmd -o -f examples/apple-appium-consumer/pom.xml verify
```

For a published release, a clean external project imports the BOM and starter:

```xml
<properties>
  <maven.compiler.release>25</maven.compiler.release>
  <taf.version>1.3.0</taf.version>
</properties>
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.codinglair.taf</groupId>
      <artifactId>codinglair-taf-bom</artifactId>
      <version>${taf.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
<dependencies>
  <dependency>
    <groupId>com.codinglair.taf</groupId>
    <artifactId>codinglair-taf-starter-mobile</artifactId>
    <type>pom</type>
  </dependency>
</dependencies>
```

Copy one of the executable configurations rather than transcribing it:

- [native preinstalled](../../examples/apple-appium-consumer/src/test/resources/capabilities/mobile.yml)
- [native packaged](../../examples/apple-appium-consumer/src/test/resources/capabilities/mobile-packaged.yml)
- [hybrid](../../examples/apple-appium-consumer/src/test/resources/capabilities/mobile-hybrid.yml)
- [mobile Safari](../../examples/apple-appium-consumer/src/test/resources/capabilities/mobile-safari.yml)

Replace every `UNRESOLVED` value with operator-approved target data. Passive `verify` compiles
and validates composition; it is not a live qualification. Authorized TestNG and Cucumber
commands are in the [consumer README](../../examples/apple-appium-consumer/README.md).

## Selection matrix

| Dimension | Supported values | Required distinction |
| --- | --- | --- |
| Platform/family | `ios`/`IPHONE`, `ipados`/`IPAD` | XCUITest sends `iOS` on the wire while TAF retains family metadata |
| Execution mode | `NATIVE`, `HYBRID`, `SAFARI` | Safari forbids application install, bundle and cleanup options |
| Device kind | `SIMULATOR`, `PHYSICAL` | Packaged build kind must match; physical builds must already be signed |
| Application mode | `PREINSTALLED`, `PACKAGED` | Native/hybrid only; preinstalled requires bundle ID |
| Topology | `LOCAL_HOST`, `REMOTE_HOST`, `PROVIDER` | Describes the Appium service, not the JVM operating system |

OS version (`platform-version`), device name and target identity remain configurable; no OS
version or device model is certified merely because it can be configured.

## Endpoints, applications and authentication

`server-url` is the exact HTTP(S) Appium base URI. Its path is preserved. User information,
query strings, fragments, traversal and encoded ambiguous paths are rejected. `LOCAL_HOST`
requires a prepared macOS/Xcode/Appium/XCUITest host. `REMOTE_HOST` and `PROVIDER` may be used
from a Windows, Linux or remote MCP worker without local Xcode.

Packaged applications use one typed server-visible reference:

```yaml
app-reference:
  kind: SERVER_PATH
  value: /authorized/apps/Consumer.app
  build-kind: SIMULATOR
```

`SERVER_PATH` is resolved by the Appium server, never uploaded from the client JVM.
`AUTHORIZED_URL` requires an authorized HTTP(S) resource. `PROVIDER_UPLOAD` is valid only with
`PROVIDER` topology and names a provider-owned upload. Use a simulator `.app`/supported archive
for a simulator and an appropriately signed `.ipa` or provider reference for a physical device.
TAF validates declared kind and reference shape, not binary signatures.

Credentials are opaque secret references and resolve only inside an authorized exchange:

```yaml
authentication:
  mechanism: HEADER
  secret-references:
    Authorization: secret://env/APPLE_AUTHORIZATION_HEADER
provider-options:
  "[cloud:options]":
    account:
      accessKey:
        secretReference: secret://env/APPLE_ACCESS_KEY
```

Do not put credentials in URLs, YAML values, capabilities, logs or evidence. `BASIC` requires
username and password references; `PROVIDER_CAPABILITY` maps approved nested capability paths to
references. A secret reference grants neither endpoint nor target access. See
[Apple transport security](apple-transport-security.md).

Provider options are namespaced, typed capability extensions; they do not imply certification.
TAF 1.3.0 supports generic Appium-compatible endpoints. No named cloud provider is certified by
the current record.

## WDA, signing and concurrency ownership

The infrastructure owner prepares Xcode, SDK/runtime, device trust, signing/team/profile,
WebDriverAgent and Appium/XCUITest. TAF may consume explicitly delegated WDA/MJPEG ports,
derived-data paths, running-WDA URLs or provider allocations. It never stores private keys,
profiles or signing values.

Each named controller has one session-local driver, context generation and evidence namespace.
TAF rejects observable collisions in target identity, host WDA/MJPEG ports and derived-data
paths, and releases only resources it reserved. Providers and operators must coordinate target,
port and WDA allocation across JVMs/workers; process-local checks cannot prove global exclusivity.
No minimum Apple concurrency is promised.

## Readiness and remote operation

Passive readiness validates configuration, authorization shape and observable prerequisites
without allocating a device. `READY`, `DEGRADED`, `UNAVAILABLE`, `MISCONFIGURED` and `UNKNOWN`
retain their existing meanings. A reachable `/status` endpoint is not proof that the requested
device, WDA, signing, application or provider allocation can create a session.

For local topology, declare only verified `doctor`, `xcode`, `device`, `signing`, `wda`,
`application`, `target`, `compatibility` and `endpoint` prerequisite outcomes. Remote and
provider-only facts remain `UNKNOWN` until initialization unless the trusted operator/provider
supplies them. Final readiness is confirmed by actual session creation. An unknown remote result
is not silently promoted to ready.

MCP discovery is passive and exposes `mobile.apple` without target allocation or secrets.
Authorized fixture execution uses the same TestSession lifecycle, policies and evidence contracts
over STDIO or Streamable HTTP. The MCP image is a remote Apple client and intentionally contains
no Xcode, simulator, WDA or Apple device tooling.

## Platform behavior and evidence

Apple operations are intentionally not Android parity shims. Reset `REUSE`, `RELAUNCH` and
authorized `REINSTALL` do not guarantee Keychain, permission or all persisted-state removal and
never erase a shared device. Simulator permission operations may require `applesimutils` or
qualified `simctl`; physical-device permission tests normally use system dialogs. Notification
production/push delivery belongs to the SUT/infrastructure, and Notification Center interaction
is target-specific. Automatic alert accept/dismiss is explicit, off by default and can invalidate
permission tests. Deep links require a compatible OS/Xcode/XCUITest tuple.

Hybrid tests must select an exact WebView using bounded discovery, then re-resolve elements after
every context change. The application must expose an inspectable WKWebView and applicable devices
must enable Safari Web Inspector. TAF does not modify the application. Mobile Safari is XCUITest
Safari, not desktop Safari or Playwright WebKit. See
[Apple operations and contexts](apple-appium-operations.md).

Screenshots, source, XCUITest syslog and video use the shared collector/redaction/reporter path.
Each requested artifact reports `available`, `unsupported`, `unavailable` or
`collection-failed`. Video is optional by default. Visual capture requires explicit policy;
provider video destinations require separate grants. Evidence is captured before owned cleanup,
and capture failure cannot skip driver teardown. See
[conditional Apple evidence](apple-evidence-and-reporting.md).

## Troubleshooting

| Symptom | Meaning and correction |
| --- | --- |
| `UNRESOLVED` preflight diagnostics | Supply authorized device/app/operator values; do not bypass consolidated preflight |
| Endpoint reachable but readiness `UNKNOWN` | Remote target/WDA/signing/application facts are not observable; confirm through trusted metadata and session creation |
| Packaged build rejected | Match `app-reference.build-kind` to `device-kind`; use a simulator build or signed physical build as appropriate |
| No WebView appears | Enable test-build inspectability/Web Inspector, verify host-device trust and use the exact context identifier |
| Permission operation rejected | Simulator-only API or missing helper/driver support; use physical-device dialog screens when applicable |
| Video/log outcome is unsupported | The server did not advertise genuine XCUITest capture; do not relabel server logs or Android Logcat |
| Parallel collision | Allocate a unique target/WDA/MJPEG/derived-data resource or use provider allocation; coordinate across workers |
| Cleanup reports a secondary failure | Preserve the original test failure; inspect sanitized cleanup evidence and confirm only owned resources were targeted |
| Remote JVM has no Xcode | Expected for `REMOTE_HOST`/`PROVIDER`; diagnose the Apple host, not the client JVM |

Exact selected locks and unverified qualification fields are maintained in the
[Apple compatibility and limitations record](apple-appium-compatibility-1.3.0.md).

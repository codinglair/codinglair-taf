# Apple Appium <!-- taf-version -->`1.3.0` compatibility and limitations record

**Record revision:** DOC-130-002 current summary

**Record status:** selected hosted simulator tuple passed; bounded limitations remain

**Immutable result:** [VER-130-002 candidate 2](../qualification/apple/VER-130-002-candidate-2.md)

This current compatibility summary separates configured support, selected versions and observed
qualification. The linked append-only candidate record is authoritative for the tested source,
hosted run and retained artifact identity. The earlier candidate-1 `NOT RUN / UNVERIFIED` record
remains historical evidence and is not rewritten by this summary.

## Selected and observed tuple

| Component | Selected lock/boundary | Actually observed for this record | Status |
| --- | --- | --- | --- |
| TAF target release | 1.3.0 | Qualified artifacts were 1.2.0 candidate coordinates from the tested checkout | Qualification passed; publication is separate |
| Java | 25 | 25 | Hosted-observed |
| Spring Boot BOM | 4.1.0 | Source POM 4.1.0 | Source-inspected |
| Appium Java client | 10.1.1 | 10.1.1 | Hosted-observed |
| Selenium BOM | 4.43.0 | 4.43.0 | Hosted-observed |
| Appium server | 3.0.0 | 3.0.0 | Hosted-observed |
| XCUITest driver | 10.0.0 | 10.0.0 | Hosted-observed |
| Node | 22.12.0 | 22.12.0 | Hosted-observed |
| npm | 10.9.0 | Exact value retained in the hosted artifact | Hosted-observed; see immutable record |
| WebDriverAgent | Resolved XCUITest 10.0.0 dependency plus recorded hash | Exact package hashes retained in the hosted artifact | Hosted-observed; never infer exact version from a range |
| macOS / runner image | Explicit workflow selection plus observed image metadata | `macos-15`; exact image/build metadata retained | Hosted-observed |
| Xcode / SDK | Explicit compatible path/version plus observed SDK | Xcode 16.4 / iOS 18.5 | Hosted-observed |
| iOS/iPadOS runtime | Configurable; one explicit simulator tuple sufficient initially | iOS 18.5 | One tuple qualified; no OS-wide certification |
| Device | iPhone/iPad; simulator/authorized physical | iPhone 16 simulator | Physical devices and iPad remain unverified |
| Application | Native/hybrid/Safari; preinstalled/packaged as applicable | Native fixture, hybrid WKWebView and Mobile Safari passed | Fixture hashes retained in hosted artifact |
| Topology | Local host, remote host, generic provider-compatible endpoint | LOCAL_HOST loopback Appium and page server | No remote/provider certification |

The server/driver/Node/npm selections are the approved qualification candidate from the Apple
contract plan. The root BOM's Java-client/Selenium pair is retained. Changes require a new record
revision and compatibility evidence; they are not silently floated.

## Evidence classification

| Layer | Current evidence | Claim boundary |
| --- | --- | --- |
| Unit/protocol | Apple configuration, W3C operations, readiness, security, evidence, isolation and MCP transport suites in dependency handoffs | Validates TAF behavior against deterministic fixtures; not a real device/session |
| Standalone consumer | `examples/apple-appium-consumer` compiles/loads native, hybrid and Safari selections outside the reactor parent | Validates public artifacts/configuration; passive default does not open a driver |
| Android regression | Dependency handoffs record existing Android suites passing with pinned Java client/Selenium pair | Preserves Android compatibility; does not qualify Apple infrastructure |
| Real simulator | Candidate 2 passed native, hybrid, Safari, screenshot/source, controlled-failure, and owned-cleanup checks | Qualified only for the linked tested SHA and tuple |
| Physical device | Unverified | Not implied by simulator results |
| Named cloud provider | None selected or certified | Generic configurable endpoint support only |

## Qualification evidence and remaining unknowns

Candidate 2 records tested SHA `ff1cf9d0ede23737966418665a9739356bb3e846`, workflow run
`37695433922` attempt 1, and retained artifact `apple-simulator-qualification-evidence` ID
`11515947412` with archive SHA-256
`12c90dff4bfa9f45ea61dc93cadcd96bd30c82d5abdb846df14f47799900ba65`. Required screenshot and
page-source evidence passed. Device-log and video collection was attempted; exact availability is
retained in the artifact and is not promoted to an unconditional claim. Physical-device, iPad,
remote-host, named-provider, broader tuple, and cross-process concurrency coverage remain
`UNVERIFIED` or `UNKNOWN`, never implied by the simulator pass.

## Known limitations

- Configurable iOS/iPadOS versions and device names are not a promise of universal compatibility.
- Simulator qualification does not certify physical signing, device trust, push notifications,
  Keychain/permission reset behavior, every orientation or every deep-link association.
- Generic remote/provider topology does not certify any named vendor, provider capability schema,
  artifact service or concurrency allocation policy.
- `/status` reachability and passive discovery do not prove device, WDA, signing or application
  readiness. Unknown remote session creation outcomes remain explicit.
- TAF does not provision Appium, Xcode, SDKs, simulators, devices, WDA, signing assets or clouds.
- Video, syslog, screenshots and source are conditional on service support and security policy;
  optional unavailable video is not a default execution failure.
- Process-local collision checks do not coordinate independent JVMs or provider tenants.
- The MCP image contains no Apple toolchain and does not turn remote Apple infrastructure into a
  locally provisioned resource.

See the [consumer guide](apple-appium-consumer-guide.md) for configuration and troubleshooting.

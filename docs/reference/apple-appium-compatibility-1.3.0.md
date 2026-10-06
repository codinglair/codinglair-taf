# Apple Appium <!-- taf-version -->`1.2.0` compatibility and limitations record

**Record revision:** DOC-130-001 candidate 1  
**Record status:** selected locks documented; live Apple qualification pending  
**Final result owner:** VER-130-002

This append-only candidate structure separates configured support, selected versions and observed
qualification. A selected value is not an executed result. VER-130-002 must create a later record
revision with the actual candidate SHA/artifact hashes and hosted run evidence; it must not rewrite
this record to imply that the pending checks ran.

## Selected and observed tuple

| Component | Selected lock/boundary | Actually observed for this record | Status |
| --- | --- | --- | --- |
| TAF target release | 1.3.0 | Current source/candidate Maven revision 1.2.0 | Candidate, not published |
| Java | 25 | Pending DOC verification environment entry | Local verification pending |
| Spring Boot BOM | 4.1.0 | Source POM 4.1.0 | Source-inspected |
| Appium Java client | 10.1.1 | Source POM 10.1.1 | Protocol/unit qualified; live Apple pending |
| Selenium BOM | 4.43.0 | Source POM 4.43.0 | Protocol/unit qualified; live Apple pending |
| Appium server | 3.0.0 | Not installed/observed | Selected qualification candidate |
| XCUITest driver | 10.0.0 | Not installed/observed | Selected qualification candidate |
| Node | 22.12.0 | Not installed/observed | Selected qualification candidate |
| npm | 10.9.0 | Not installed/observed | Selected qualification candidate |
| WebDriverAgent | Resolved XCUITest 10.0.0 dependency plus recorded hash | Pending | Must be observed; never infer exact version from a range |
| macOS / runner image | Explicit workflow selection plus observed image metadata | Pending | VER-130-002 |
| Xcode / SDK | Explicit compatible path/version plus observed SDK | Pending | VER-130-002 |
| iOS/iPadOS runtime | Configurable; one explicit simulator tuple sufficient initially | Pending | No OS-wide certification |
| Device | iPhone/iPad; simulator/authorized physical | Pending | No model-wide certification |
| Application | Native/hybrid/Safari; preinstalled/packaged as applicable | Pending fixture identity/hash | VER-130-002 |
| Topology | Local host, remote host, generic provider-compatible endpoint | Protocol fixtures only | No provider certification |

The server/driver/Node/npm selections are the approved qualification candidate from the Apple
contract plan. The root BOM's Java-client/Selenium pair is retained. Changes require a new record
revision and compatibility evidence; they are not silently floated.

## Evidence classification

| Layer | Current evidence | Claim boundary |
| --- | --- | --- |
| Unit/protocol | Apple configuration, W3C operations, readiness, security, evidence, isolation and MCP transport suites in dependency handoffs | Validates TAF behavior against deterministic fixtures; not a real device/session |
| Standalone consumer | `examples/apple-appium-consumer` compiles/loads native, hybrid and Safari selections outside the reactor parent | Validates public artifacts/configuration; passive default does not open a driver |
| Android regression | Dependency handoffs record existing Android suites passing with pinned Java client/Selenium pair | Preserves Android compatibility; does not qualify Apple infrastructure |
| Real simulator | Not run in this record | VER-130-002 must record native/hybrid/Safari/evidence/lifecycle outcomes |
| Physical device | Unverified | Not implied by simulator results |
| Named cloud provider | None selected or certified | Generic configurable endpoint support only |

## Pending qualification fields

VER-130-002 must append a versioned record containing:

- tested Git SHA/tree identity and candidate Maven artifact hashes;
- workflow URL, run ID, attempt, trigger and immutable retained artifact identity;
- selected and observed macOS runner image, Xcode, SDK/runtime and simulator device tuple;
- observed Java, Appium, XCUITest, Node, npm and WDA version/source hash;
- fixture application and local Safari-page source/build hashes;
- family, device kind, execution/application mode and topology actually exercised;
- native, hybrid, Safari, evidence, controlled-failure and owned-cleanup outcomes;
- Android regression and standalone consumer evidence references;
- artifact availability for screenshot, source, genuine device logs and video;
- setup/test/cleanup failure disposition and every configuration left unverified.

Pending values must remain `NOT RUN`, `UNVERIFIED` or `UNKNOWN`; blank fields and protocol mocks
must never be presented as a hosted pass.

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

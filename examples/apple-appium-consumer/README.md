# Standalone Apple Appium consumer

This Java 25 project uses the published TAF BOM/mobile starter and independent
Cucumber runner. It has no reactor parent, framework test fixtures or bundled application.
The current candidate artifact version is 1.2.0; select a locally installed/staged candidate
with `-Dtaf.version=<version>`. The Apple increment targets release 1.3.0.

From the repository root, verify offline against installed candidate artifacts:

```powershell
$tafVersion = .\mvnw.cmd -B -ntp -Dstyle.color=never help:evaluate -Dexpression=revision -q -DforceStdout
.\mvnw.cmd -o -f examples/apple-appium-consumer/pom.xml "-Dtaf.version=$tafVersion" verify
```

From a standalone copy, use `mvn verify` with your approved Maven settings/repository.
Default tests compile the interactions, load the Spring context and named lazy Apple controller,
test shared task success/failure/native return, and exercise independent Cucumber composition.
They never open a driver. These deterministic checks do not qualify real Apple infrastructure.

Select an authorized consumer app/page with known selectors and expected text. Set the
environment variables described in [APPLE.md](APPLE.md), including target identity.
`UNRESOLVED` placeholders deliberately fail consolidated preflight. Configure verified
operator/provider `prerequisites`, WDA/signing and secret references according to
[the Appium module guide](../../codinglair-taf-runtime/taf-mobile-appium/README.md)
and [transport security](../../docs/reference/apple-transport-security.md).
Those repository links are guidance; a standalone copy has no source dependency on them.

| Selection | Configuration | Assertion |
| --- | --- | --- |
| Native preinstalled | `capabilities/mobile.yml` | Tap accessibility identifier and assert resulting text |
| Native packaged | `capabilities/mobile-packaged.yml` | Same interaction using a typed server-side app reference |
| Hybrid | `capabilities/mobile-hybrid.yml` | Select exact WebView, assert DOM text, return to native |
| Safari | `capabilities/mobile-safari.yml` | Navigate authorized URL and assert DOM text |

Set `APPLE_PLATFORM=ipados` and `APPLE_FAMILY=IPAD` for iPad; defaults are ios/IPHONE.
Set `APPLE_DEVICE_KIND=PHYSICAL` for authorized physical targets, with matching signed build
when packaged. `APPLE_TOPOLOGY` selects LOCAL_HOST, REMOTE_HOST or PROVIDER;
`APPLE_SERVER_URL` always names the customer/provider endpoint. Local host execution needs
equipped macOS/Xcode/WDA; a remote client does not provision or probe local Apple tools.
Configure provider selection/authentication explicitly in the YAML when using a device cloud.

Run technical TestNG interaction (replace the configuration for other selections):

```powershell
.\mvnw.cmd -f examples/apple-appium-consumer/pom.xml '-Dtaf.version=<version>' '-Dexample.configuration=capabilities/mobile-hybrid.yml' '-Dsurefire.suiteXmlFiles=src/test/resources/testng-apple.xml' test
```

Run the independent curated Cucumber interaction:

```powershell
.\mvnw.cmd -f examples/apple-appium-consumer/pom.xml '-Dtaf.version=<version>' '-Dexample.configuration=capabilities/mobile-safari.yml' '-Dtest=AppleBehaviorRunner' test
```

Both reuse `InteractionTask` and session-local `AppleScreen`. TestNG inherits `TafBaseTest`;
Cucumber uses `AbstractCucumberRunner` and framework hooks, with a scenario-owned Spring
object factory because the existing Cucumber artifact uses Pico rather than Spring injection.
No consumer lifecycle is copied. Unresolved execution prerequisites fail before driver use.
No credentials, app binaries, signing material or mandatory paid fixtures are supplied.

Use the existing governed blueprint scaffolding service to generate a mobile project with
IOS/IPADOS, family, execution/application mode, target kind, topology and runner selections;
see [the composition contract](../../docs/reference/blueprint-composition-contract.md).
Failed selection/collision validation produces an empty write set. Android remains the default.

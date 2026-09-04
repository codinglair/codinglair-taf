# Codinglair TAF Quick Start

This guide is for Test Automation Engineers and SDETs creating a Java automation project for a
system under test (SUT). It explains the released Codinglair TAF Runtime capabilities and the
optional MCP-assisted workflow. It is not a framework-maintainer build guide.

> **Example status:** Class names, method names, configuration namespaces, dependencies, and MCP
> operations in this guide were checked against public repository sources. Unless a section says
> otherwise, treat the snippets as API-verified excerpts—not as proof that your adapted project has
> compiled or run. Replace every application-specific placeholder before execution and verify the
> result in your own consumer project.

## 1. Choose Runtime, MCP, or both

Codinglair TAF has two independently useful surfaces:

- **TAF Runtime** supplies deterministic Java test capabilities: sessions, web, mobile, REST, SOAP,
  files, databases, messaging, contracts, test definitions, environments, reporting, and runners.
  Use Runtime directly when you write and run tests from an IDE, Maven, or CI.
- **TAF MCP Server** gives an authorized MCP client a governed way to validate, compile, build,
  execute, monitor, retrieve sanitized resources/evidence, and cancel work. It is an execution and
  coordination surface around an existing test project.

MCP does **not** replace Java test code. Your project still owns its pages and components, mobile
screens, API models, database queries, message models, assertions, test data, and business cleanup.
MCP exposes no unrestricted shell, browser, SQL, broker, or filesystem tool.

Start with direct Runtime usage. Add MCP only when your team needs controlled agent-assisted
validation or execution.

## 2. Prerequisites and SUT information

### 2.1 Workstation prerequisites

- Java 25
- Git
- the Maven Wrapper copied into the consumer project (`mvnw`, `mvnw.cmd`, and `.mvn/wrapper`)
- access to an approved repository containing the root POM <!-- taf-version -->`1.0.0` of Codinglair TAF, or an approved locally
  staged build
- Docker only for capabilities that provision containers
- external infrastructure for opt-in capabilities such as Appium, Android devices, databases, and
  brokers

Verify the workstation:

```powershell
java -version
git --version
```

```bash
java -version
git --version
```

Java and the wrapper must use Java 25. Honor the organization's Maven `settings.xml`, mirrors,
proxies, and certificates. Do not bypass TLS or add an arbitrary public repository when an approved
mirror is required.

### 2.2 Information to obtain before writing tests

Collect the following from the application and environment owners:

- authorized test URLs, API contracts, WSDL/XSDs, message schemas, and supported browsers/devices;
- acceptance criteria and identifiers that correlate one transaction across layers;
- test identities, roles, and **secret references**—not resolved credentials;
- approved database views/tables, read-only validation accounts, and sensitive columns;
- broker addresses, destinations, consumer-group rules, and message ownership;
- test-data source of truth, case identifiers, payload locations, and deletion policy;
- maximum business-processing times for bounded polling;
- permitted screenshots, video, traces, response bodies, snapshots, and retention; and
- CI environment aliases, approval rules, resource limits, and artifact-retention rules.

Do not target production unless a capability explicitly supports a non-destructive production mode
and your organization has separately approved it. The examples in this guide assume an authorized
non-production environment.

## 3. Create a clean consumer project

### 3.1 Start from the golden project

The recommended starting point is
[`demos/playwright-sauce-demo`](../demos/playwright-sauce-demo/). It is a Spring Boot consumer with
the wrapper, typed configuration, TestNG and Cucumber suites, pages, workflows, paired test data,
and report configuration.

If your organization has not published the root POM `revision`, an authorized maintainer can stage it from a
TAF source checkout. This is a framework-source action, not a normal consumer command:

```powershell
.\mvnw.cmd clean deploy -Prelease-staging
```

```bash
./mvnw clean deploy -Prelease-staging
```

Copy the golden project into a clean repository. If the corrected DOC-001 standalone POM is present
in the source checkout, use it because it has no reactor-relative parent:

```powershell
New-Item -ItemType Directory my-taf-tests
Copy-Item -Recurse demos\playwright-sauce-demo\* my-taf-tests
Copy-Item docs\examples\quick-start-pom.xml my-taf-tests\pom.xml -Force
Set-Location my-taf-tests
.\mvnw.cmd -DskipTests test-compile
```

```bash
mkdir my-taf-tests
cp -R demos/playwright-sauce-demo/. my-taf-tests/
cp docs/examples/quick-start-pom.xml my-taf-tests/pom.xml
cd my-taf-tests
./mvnw -DskipTests test-compile
```

If `docs/examples/quick-start-pom.xml` has not yet been committed or published with your release,
ask the TAF distributor for the release-approved consumer POM. Do not use a POM that inherits from
the TAF source reactor.

### 3.2 Keep only required dependencies

Import the Codinglair TAF BOM and declare only the capabilities used by the project. The golden
consumer/standalone POM is the version authority; do not independently version BOM-managed TAF
artifacts.

Typical dependency choices are:

| Need | Artifact |
| --- | --- |
| Session and controller lifecycle | `codinglair-taf-runtime-core` |
| TestNG lifecycle | `codinglair-taf-runner-testng` |
| Cucumber lifecycle | `codinglair-taf-runner-cucumber` |
| Browser UI | `taf-web-playwright` |
| Android/Appium | `taf-mobile-appium` |
| REST / SOAP | `taf-api-rest` / `taf-api-soap` |
| JDBC validation | `taf-database` plus the approved JDBC driver |
| Structured files | `taf-file` |
| Test data | `taf-test-definitions`; optionally `taf-test-definitions-mongodb` |
| Local secret provider | `taf-secrets-local` (optional) |
| Kafka / RabbitMQ / JMS | one matching messaging adapter |
| OpenAPI / AsyncAPI structure | `taf-contracts` |
| Service virtualization | `taf-virtualization-wiremock` |
| Environment composition | `taf-environments` |
| Flyway setup | `taf-data-migration` |
| Allure adapter | `codinglair-taf-reporting-allure` |
| Structural consumer checks | `taf-consumer-conformance` |

`codinglair-taf-common`, `taf-mobile-core`, and `taf-messaging-core` are support/SPI artifacts, not
standalone user capabilities. The MCP aggregate and its contracts/jobs/security/worker/resource/
tool/prompt/transport modules are server-side components; do not add them to an ordinary Runtime
test project.

### 3.3 Recommended project layout

```text
src/main/java/com/example/automation/
  AutomationApplication.java
  configuration/        Spring composition and typed properties
  page/ component/      Playwright objects
  screen/               Appium objects
  service/              Business workflows and API resources
  repository/           Application-specific database queries
  model/                Inputs, expected outputs, responses, messages
src/test/java/com/example/automation/
  functional/           Technical TestNG tests
  bdd/runner/            Cucumber suite runners
  bdd/steps/             Cucumber step definitions
  f2b/                  Cross-layer scenarios
src/test/resources/
  application.yaml
  application-local.yaml
  application-ci.yaml
  features/
  suites/
  test-data/
```

Keep SUT-specific wiring under your application's configuration package. Do not modify Runtime
modules or construct framework controllers directly.

## 4. Configuration and secret handling

### 4.1 Profiles and named capabilities

Use `application.yaml` for the stable configuration shape, `application-local.yaml` for safe local
overrides, and `application-ci.yaml` for CI execution choices. Official consumers are non-web
applications by default:

```yaml
spring:
  main:
    web-application-type: none

taf:
  consumer:
    environment: ${TAF_ENVIRONMENT:local}
    capabilities:
      storefront:
        type: web-playwright
        required-values:
          base-url: ${STOREFRONT_URL:REPLACE_ME}
        secret-references:
          password: STOREFRONT_PASSWORD
        enabled: true
```

`REPLACE_ME`, `example.test`, example package names, selectors, SQL, destinations, and application
identifiers in this guide are **application-specific placeholders**. In contrast, `taf.*` property
names, public Java types, and operation names are framework contracts.

The consumer declaration and module configuration serve different purposes:

| Layer | Example | Released responsibility |
| --- | --- | --- |
| Consumer capability declaration | `taf.consumer.capabilities.storefront` | Declares the logical selection and contributes generic required-value, secret-alias, dependency, incompatibility, and provider-readiness checks to preflight. |
| Module controller configuration | `taf.web.playwright.controllers.storefront` or `taf.api.rest.controllers.orders-api` | Binds module-specific settings and causes that module's auto-configuration to register the named controller in each session. |

The module layer is required to create a specifically named controller. The consumer declaration is
required for the guide's generic capability-selection/preflight workflow, but it does not create a
controller. Use the same logical name in both layers (`storefront` with `storefront`, `orders-api`
with `orders-api`) so selection, diagnostics, and controller lookup describe the same SUT boundary.
The released code binds these maps independently and does not automatically enforce that the names
match; preflight from each layer remains complementary rather than an implicit join.

Call `ConsumerPreflight.verify()` immediately before controlled environment execution. It reports
unresolved required values, unavailable secret aliases, incompatible selections, and contributed
readiness failures without resolving or printing secrets. A scaffolded context may load before all
values exist; preflight is the execution gate.

### 4.2 Secret references

The released Secrets API accepts these canonical forms:

- `secret://env/UPPER_CASE_VARIABLE`
- `secret://jasypt/<base64-or-base64url-ciphertext>`
- `credential://lower-case-profile-alias`—reserved; no approved profile provider is released

Store only the reference. `SecretManager` resolves it inside the deterministic execution boundary
and returns a short-lived `ResolvedSecret`. Never put the resolved value in source, YAML, CSV,
Gherkin, MCP payloads, command arguments, logs, screenshots, reports, or artifacts.

`taf-secrets-local` supplies environment and Jasypt providers. Jasypt is intended for local,
air-gapped, or legacy-compatible use; managed environments should use an approved enterprise
provider when one is available. Vault, cloud, Kubernetes, and workload-identity providers are not
included in this release.

Local Jasypt configuration names the bootstrap environment variable; it does not contain the key:

```yaml
taf:
  secrets:
    jasypt-master-key-environment-variable: ${TAF_JASYPT_MASTER_KEY_ENV:REPLACE_ME}
```

Enter temporary values interactively. Avoid literal secrets in shell history:

```powershell
$env:TAF_JASYPT_MASTER_KEY_ENV = "TAF_JASYPT_MASTER_KEY"
$env:TAF_JASYPT_MASTER_KEY = Read-Host -MaskInput "Temporary local Jasypt master key"
```

```bash
export TAF_JASYPT_MASTER_KEY_ENV=TAF_JASYPT_MASTER_KEY
read -rsp 'Temporary local Jasypt master key: ' TAF_JASYPT_MASTER_KEY
export TAF_JASYPT_MASTER_KEY
printf '\n'
```

Clear interactive secrets after execution:

```powershell
Remove-Item Env:TAF_JASYPT_MASTER_KEY -ErrorAction SilentlyContinue
```

```bash
unset TAF_JASYPT_MASTER_KEY
```

Plaintext is never a fallback. If test-data secret fields need normalization, perform the explicit
authorized preparation operation on an isolated copy; normal repository reads enforce references
and do not rewrite their source.

In the earlier `storefront` declaration, `password: STOREFRONT_PASSWORD` means preflight checks
whether the Spring environment contains the logical property key `STOREFRONT_PASSWORD`; it is not a
resolved password and is not the value passed to `SecretManager.resolve(...)`. The execution
environment supplies the secret value under that key, while test data or an application-owned
workflow carries the opaque resolution reference `secret://env/STOREFRONT_PASSWORD`. There is no
`STOREFRONT_PASSWORD_REF` setting in this flow.

## 5. Lifecycle, organization, assertions, and test data

### 5.1 One session per invocation

`TestSession` owns invocation-scoped controllers, correlation context, evidence, and cleanup.
Controllers are lazy and close with the session. Do not store a `TestSession`, controller,
Playwright page/locator, Appium screen/driver, message consumer, native connection, test ID, or
mutable input in static or singleton state.

Spring singleton factories may retain `SessionAwareAccessor`, stable configuration, and
`PlaywrightObjectFactory`, then resolve invocation-owned objects inside each factory method.

### 5.2 Test definitions

`taf-test-definitions` provides CSV and JSON/YAML file providers plus the
`TestDefinitionRepository` SPI. Recommended paired CSV files keep inputs and expected outputs
separate and join exactly one row from each file by a configured case-ID column. Duplicate or
missing IDs and conversion failures fail with categorized diagnostics.

Model typed data as Java records. Persistent definitions contain inputs and expected outputs;
actual outputs and execution metadata belong in `TestExecutionData`. Secret fields contain only an
opaque test-definition `SecretReference` and can be classified with `@SecretField` or CSV schema
metadata.

TestNG code calls `TafBaseTest.testDefinition(...)`; Cucumber glue calls
`CucumberScenarioSession.testDefinition(...)`. Prefer these lifecycle-owned access points over
opening repositories in every test.

JSON/YAML repositories are schema-versioned and use optimistic version checks. They do not justify
silently overwriting concurrent changes. Binary or large payloads are represented by
`PayloadReference` metadata and resolved through a bounded `PayloadResolver`.

The optional MongoDB adapter is selected with `taf.context.repository=mongodb`. It is for the
reserved framework-context data plane, not an SUT database. Its project, authority, schema version,
and collection must be explicit. It validates schema drift but does not repair or rewrite ordinary
documents. Use MongoDB only when your team has an approved repository workflow; CSV/file providers
require less infrastructure.

### 5.3 Assertions and bounded polling

Use TestNG or AssertJ assertions. Do not use Java `assert`, which may be disabled. Assert meaningful
business values at each boundary and include safe identifiers in failure messages.

For eventually consistent behavior, use `AwaitableAssertion` with a business-approved deadline and
interval. Do not use an unbounded loop or replace polling with a fixed `Thread.sleep`. Timeout
diagnostics should contain the last **sanitized** observation and correlation ID, never credentials
or sensitive payloads.

API-verified use:

```java
AwaitableAssertion.AwaitableAssertionResult result =
    AwaitableAssertion.create(
            "order reaches COMPLETE",
            ignored -> "COMPLETE".equals(readStatus(orderId)),
            Duration.ofSeconds(30),
            Duration.ofMillis(500))
        .await(null, "orderId=" + orderId);

Assert.assertTrue(result.isSuccessful(), result.getMessage());
```

`readStatus` is application-owned. The predicate calls it again for each attempt; false observations
are retried until success or timeout. A thrown `Exception` produces a failed result rather than a
retry, and an `AssertionError` escapes, so keep intermediate assertions outside the predicate.
Ensure observations and the optional context are safe for diagnostics. The repository's complete
quick-start database example is the preferred source when available.

## 6. TestNG and Cucumber

### 6.1 TestNG for technical verification

Extend `TafBaseTest`. It creates one session per TestNG method, binds reporting and test data, runs
preflight, and closes the session after pass, failure, or skip. Do not create a second session.

```java
@SpringBootTest(classes = AutomationApplication.class)
@ContextConfiguration(classes = AutomationApplication.class, inheritLocations = false)
public final class LoginTest extends TafBaseTest {

  @Test
  @TestCaseId("TC0001")
  public void userCanLogIn() {
    LoginInput input = testInput(LoginInput.class);
    // Application-owned workflow; resolves the secret reference only at execution.
    loginWorkflow.authenticate(input.username(), input.passwordReference());
    Assert.assertEquals(productsPage.title(), "Products");
  }
}
```

`AutomationApplication`, `LoginInput`, `loginWorkflow`, and `productsPage` are consumer-owned
placeholders. The maintained implementation pattern is in
[`SauceDemoLoginTest.java`](../demos/playwright-sauce-demo/src/test/java/com/codinglair/taf/demo/sauce/functional/SauceDemoLoginTest.java).

Run a class or a technical suite:

```powershell
.\mvnw.cmd -Dtest=LoginTest test
.\mvnw.cmd -Pfunctional-suite test
```

```bash
./mvnw -Dtest=LoginTest test
./mvnw -Pfunctional-suite test
```

### 6.2 Cucumber for curated business behavior

Create a small runner and include the framework glue:

```java
@CucumberOptions(
    features = "classpath:features/account_balance.feature",
    glue = {"com.example.automation.bdd", "com.codinglair.taf.runtime.cucumber"},
    plugin = "com.codinglair.taf.runtime.cucumber.CucumberBusinessReportPlugin")
public final class AccountAcceptanceRunner extends AbstractCucumberRunner {}
```

The framework glue registers `TafCucumberHooks` and creates one session per scenario. Do not copy,
replace, or wrap those hooks. Use one `@test-case-<id>` tag when a scenario needs a stable external
ID; step definitions can read it through `CucumberScenarioSession.testCaseId()`.

Keep technical TestNG classes and Cucumber runners in separate XML suites when separate report
output is required:

```powershell
.\mvnw.cmd test "-Dsurefire.suiteXmlFiles=src/test/resources/suites/account-acceptance.xml"
```

```bash
./mvnw test -Dsurefire.suiteXmlFiles=src/test/resources/suites/account-acceptance.xml
```

Never put credentials in Gherkin examples or step text. Use a stable persona/input key that maps to
an opaque secret reference.

## 7. Released Runtime capabilities

Each optional capability must be present as a dependency and explicitly enabled/configured. Named
controllers are acquired from the active session with `controller(Type.class, "name")` or
`session.getController(Type.class, "name")`. Never instantiate a controller directly.

### 7.1 Browser UI with Playwright

```yaml
taf:
  consumer:
    capabilities:
      storefront:
        type: web-playwright
  web:
    playwright:
      enabled: true
      controllers:
        storefront:
          base-url: ${STOREFRONT_URL:REPLACE_ME}
          engine: chromium
          headless: true
```

Acquire the invocation-owned controller:

```java
PlaywrightController web = controller(PlaywrightController.class, "storefront");
```

Page/component classes own private immutable selector specifications. They may obtain
invocation-owned objects from `PlaywrightObjectFactory`, but must not cache `Page` or `Locator` in
static or singleton fields. The maintained reference is the
[`SauceDemo golden project`](../demos/playwright-sauce-demo/README.md).

Run the project's opt-in browser profile only after supplying authorized values:

```powershell
$env:STOREFRONT_URL = "https://test.example"
$env:STOREFRONT_PASSWORD = Read-Host -MaskInput "Test password"
.\mvnw.cmd -Pbrowser-smoke verify
Remove-Item Env:STOREFRONT_PASSWORD -ErrorAction SilentlyContinue
```

```bash
export STOREFRONT_URL=https://test.example
read -rsp 'Test password: ' STOREFRONT_PASSWORD && export STOREFRONT_PASSWORD
printf '\n'
./mvnw -Pbrowser-smoke verify
unset STOREFRONT_PASSWORD
```

Playwright browsers are installed through the approved Maven build path. Node.js and `npx` are not
consumer prerequisites.

### 7.2 REST API

```yaml
taf:
  api:
    rest:
      enabled: true
      controllers:
        orders-api:
          base-url: ${ORDERS_API_BASE_URL:REPLACE_ME}
          timeout: 30s
```

```java
RestController orders = controller(RestController.class, "orders-api");
RestResponse created = orders.execute(
    RestRequest.request(Method.POST, "/orders")
        .header("X-Correlation-Id", testSession().getCorrelationContext().getTraceId())
        .body("{\"sku\":\"BACKPACK\",\"quantity\":1}", "application/json")
        .build())
    .assertStatus(201);

String orderId = created.nativeResponse().jsonPath().getString("id");
Assert.assertNotNull(orderId);
```

The request data and endpoint are application-specific. The immutable `RestRequest`,
`RestResponse` assertions, path/query parameters, and capability-local `nativeSpecification()`
escape hatch are public APIs. Configure redaction before capturing exchange evidence. The corrected
DOC-001 source `RestApiExample.java` is the preferred complete positive/negative reference when it
is present in the repository.

`RestResponse.assertHeader(name, expected)` performs exact equality. Use it for `Content-Type` only
when the SUT contract requires an exact value; otherwise a valid response such as
`application/json;charset=UTF-8` would fail an assertion against `application/json`. The released
facade has no media-type-aware or partial-header assertion; use the native response escape hatch
for an application-owned tolerant check when the contract permits parameters.

### 7.3 SOAP API

Enable named lazy controllers:

```yaml
taf:
  api:
    soap:
      enabled: true
      timeout: 30s
      max-response-bytes: 10485760
      controllers:
        billing-soap:
          endpoint: ${BILLING_SOAP_ENDPOINT:REPLACE_ME}
```

Acquire `SoapController` by name. The released controller supports SOAP 1.1/1.2 envelopes, faults,
XSD/XPath/comparison utilities, and multipart attachments. Binary attachment bodies are represented
by metadata and size in evidence.

Typed clients are consumer-local. Generate CXF sources into `target/generated-sources/cxf` from
consumer-owned WSDL/XSD inputs and prove clean regeneration. Do not commit generated output or move
generated types into Runtime Core. `UsernameTokenSecurity` and
`SecretReferenceCallbackHandler` accept secret references through `SecretManager`; never put raw
passwords or keys in properties.

No clean consumer fault/authentication example is released yet. WS-Trust, WS-SecureConversation,
WS-Federation, Kerberos, SAML issuance, and automatic WS-Policy negotiation are unavailable.

### 7.4 Database validation

```yaml
taf:
  database:
    enabled: true
    environment: ${TEST_ENVIRONMENT:integration}
    connections:
      orders-db:
        technology: postgresql
        jdbc-url: ${ORDERS_DB_JDBC_URL:REPLACE_ME}
        username: ${ORDERS_DB_USERNAME:REPLACE_ME}
        password-reference: ${ORDERS_DB_PASSWORD_REF:REPLACE_ME}
        mode: external
        access: read-only
        timeout: 10s
        sensitive-columns: [customer_email, access_token]
```

```java
DatabaseController database = controller(DatabaseController.class, "orders-db");
QueryResult result = database.query(
    "select order_id, status, quantity from orders where order_id = ?", orderId);

Assert.assertEquals(result.rowCount(), 1);
Map<String, Object> row = result.rows().getFirst();
Assert.assertEquals(row.get("order_id"), orderId);
Assert.assertEquals(row.get("status"), "COMPLETE");
```

Use parameterized SQL and an approved read-only account. The controller provides bounded query,
update, transaction, setup, cleanup, and native JDBC operations, but read-only connections reject
writes. Setup and cleanup require explicit per-connection authorization. External connections never
receive implicit deletion. Native connections are an advanced escape hatch and should also use
try-with-resources.

Session close releases JDBC resources; it does not delete SUT business data. SQL, parameters, URLs,
usernames, password references, credentials, and configured sensitive values are excluded from
database evidence.

### 7.5 Structured files and payloads

```yaml
taf:
  file:
    enabled: true
    sandboxes:
      exports:
        root: ${EXPORT_SANDBOX_ROOT:REPLACE_ME}
        maximum-size: 10MB
```

`FileController` compares JSON, XML, CSV, `.xlsx`, PDF, and fixed-width files inside named
sandboxes. Use explicit ignored JSON/XML paths, numeric tolerance, CSV delimiter/charset, or
fixed-width column widths as applicable. Results are immutable `FileValidationResult` values.

Paths are confined beneath the real sandbox root. Traversal, symlink escape, missing/non-regular
files, oversized files, XML DTDs, and external entities are rejected. PDF comparison intentionally
uses digest and size; it does not extract document text into reports.

`FilePayloadResolver` resolves bounded `file:` or Git-worktree-relative `git:` payload references,
validates size/media type/SHA-256, and returns a closeable `ResolvedPayload`. A complete golden
consumer scenario for file comparison is not included in this release; use the module README and
public signatures when adding it, and verify the project before relying on the workflow.

### 7.6 Mobile Android with Appium

```yaml
taf:
  mobile:
    android:
      enabled: true
      controllers:
        customer-android:
          server-url: ${TAF_ANDROID_APPIUM_URL:http://127.0.0.1:4723}
          device-name: ${TAF_ANDROID_DEVICE_NAME:REPLACE_ME}
          device-id: ${TAF_ANDROID_DEVICE_ID:}
          device-mode: local-emulator
          application-mode: preinstalled
          app-package: ${TAF_ANDROID_APP_PACKAGE:REPLACE_ME}
          app-activity: ${TAF_ANDROID_APP_ACTIVITY:.MainActivity}
          command-timeout: 2m
          terminate-app-on-close: true
          screenshot-on-failure: true
          page-source-on-failure: true
          device-logs: true
          video: false
          allow-visual-artifacts: true
```

The module connects to an existing Appium 2 endpoint and provisioned Android device. It does not
start Appium, create an emulator, install UiAutomator2, or authorize a device. Packaged applications
installed by controller operations are removed on close by default; preinstalled applications are
never uninstalled. Visual artifacts require explicit authorization.

`AndroidController.find(String)` accepts an Android UIAutomator expression. Keep selectors as
private constants in an application-owned screen object:

```java
public final class LoginScreen {
  private static final String USERNAME =
      "new UiSelector().resourceId(\"com.example.app:id/username\")";
  private static final String PASSWORD =
      "new UiSelector().resourceId(\"com.example.app:id/password\")";
  private static final String SIGN_IN =
      "new UiSelector().resourceId(\"com.example.app:id/sign_in\")";

  private final AndroidController android;

  public LoginScreen(AndroidController android) {
    this.android = android;
  }

  @ScreenAction("Sign in on Android")
  public void signIn(String username, String resolvedPassword) {
    android.type(android.find(USERNAME), username);
    android.type(android.find(PASSWORD), resolvedPassword);
    android.tap(android.find(SIGN_IN));
  }

  @Validation("Verify Android home screen")
  public void verifyHomeScreen() {
    Assert.assertTrue(android.pageSource().contains("Home"));
  }
}
```

The test obtains the named controller from the active session. Resolve the password reference only
inside the authorized workflow; do not pass a literal password from YAML or test data:

```java
@Test
@TestCaseId("MOB-LOGIN-001")
public void userCanSignInOnAndroid() {
  AndroidController android = controller(AndroidController.class, "customer-android");
  android.launch();

  SecretRequestContext secretRequest =
      new SecretRequestContext(
          "MobileLoginWorkflow",
          testSession().getSessionId(),
          "integration",
          true);
  try (ResolvedSecret password =
      secretManager.resolve(loginInput.passwordReference(), secretRequest)) {
    new LoginScreen(android).signIn(loginInput.username(), password.useAsString());
  }

  new LoginScreen(android).verifyHomeScreen();
}
```

Direct `new LoginScreen(android)` construction intentionally demonstrates the public
`AndroidController` and an application-owned screen-object pattern. That object is not
Spring-managed, so `@ScreenAction` and `@Validation` are **not intercepted** in this minimal
example. Consumers requiring annotation-driven reporting must obtain the screen or its workflow
through an approved Spring-managed, session-aware composition pattern. This snippet does not claim
that reporting interception is demonstrated or runtime-verified.

The public controller, `find`, `type`, `tap`, `launch`, `pageSource`, secret-resolution, and
annotation calls are API-verified. `secretManager`, `loginInput`, and the authorization decision
used to construct `SecretRequestContext` are consumer-owned composition. Derive the environment
and authorization decision from trusted runtime policy rather than hard-coding them in production
test infrastructure. Prefer a workflow that minimizes the lifetime and copies of the resolved
`String`.

Use only a dedicated non-production physical device. Confirm it with `adb devices` and explicitly
select `authorized-physical-device`. Personal/production devices, iOS/XCUITest, and cloud device
providers are outside this release. The repository's `containers/android-emulator` stack is
infrastructure reference material; the selectors and assertions above remain application-owned.

### 7.7 Messaging: Kafka, RabbitMQ, and JMS

`taf-messaging-core` defines adapter-neutral `MessagingController` contracts. Select exactly the
native adapter matching the SUT: `taf-messaging-kafka`, `taf-messaging-rabbitmq`, or
`taf-messaging-jms`.

Application tests should publish or consume typed application messages through an invocation-owned
named controller, use unique correlation keys and isolated destinations/groups where possible, and
apply bounded waits. They must acknowledge only messages owned by the test and must not purge a
shared destination as generic cleanup.

The common public API uses `MessageEnvelope`, `Correlation`, `MessageQuery`, `MessageSelector`,
`ConsumptionResult`, and `MessageRecord`. A timeout returns `ConsumptionResult.Status.NO_MATCH`; it
does not return `null`.

#### Kafka

```yaml
taf:
  messaging:
    kafka:
      enabled: true
      controllers:
        orders-kafka:
          bootstrap-servers: [${KAFKA_BOOTSTRAP_SERVERS:REPLACE_ME}]
          group-id: ${KAFKA_GROUP_ID:taf-orders}
          client-id-prefix: taf-orders
          topic-policy: require-existing
          operation-timeout: 10s
          maximum-buffered-records: 1000
```

```java
KafkaController kafka = controller(KafkaController.class, "orders-kafka");
Correlation correlation = new Correlation("orderId", orderId);
MessageEnvelope message = new MessageEnvelope(
    orderEventJson().getBytes(StandardCharsets.UTF_8),
    Map.of("content-type", "application/json"),
    Optional.of(correlation));

kafka.publish("orders.events", message);

ConsumptionResult consumed = kafka.consume(
    new MessageQuery(
        "orders.events",
        MessageSelector.correlated(correlation),
        Duration.ofSeconds(20)),
    "taf-orders-" + testSession().getSessionId());

Assert.assertEquals(consumed.status(), ConsumptionResult.Status.MATCHED);
String payload = new String(
    consumed.record().orElseThrow().envelope().payload(), StandardCharsets.UTF_8);
Assert.assertTrue(payload.contains(orderId));
```

The property names and Java calls are API-verified. The topic, event JSON, headers, and group naming
are application-specific. `bootstrap-servers` is a list; if an environment variable contains
multiple comma-separated brokers, verify Spring binding in the consumer profile. The released
settings do not expose a full enterprise Kafka authentication/TLS property model; use an approved
adapter extension rather than putting credentials in these fields.

#### RabbitMQ

```yaml
taf:
  messaging:
    rabbitmq:
      enabled: true
      controllers:
        orders-rabbit:
          addresses: ${RABBITMQ_ADDRESSES:REPLACE_ME}
          username: ${RABBITMQ_USERNAME:REPLACE_ME}
          password-reference: ${RABBITMQ_PASSWORD_REF:REPLACE_ME}
          virtual-host: ${RABBITMQ_VHOST:/}
          operation-timeout: 10s
          maximum-buffered-records: 1000
```

```java
RabbitController rabbit = controller(RabbitController.class, "orders-rabbit");
RabbitTopology topology =
    new RabbitTopology("orders.test", "orders.completed.test", "orders.completed");
Correlation correlation = new Correlation("orderId", orderId);

rabbit.declareTopology(topology);
rabbit.publish(
    topology,
    new MessageEnvelope(
        orderEventJson().getBytes(StandardCharsets.UTF_8),
        Map.of("content-type", "application/json"),
        Optional.of(correlation)));

ConsumptionResult consumed = rabbit.consume(
    new MessageQuery(
        topology.queue(),
        MessageSelector.correlated(correlation),
        Duration.ofSeconds(20)),
    RabbitAcknowledgment.ACK);

Assert.assertEquals(consumed.status(), ConsumptionResult.Status.MATCHED);
```

Use non-durable, uniquely namespaced test topology unless the environment owner supplies an
existing topology. `NACK_REQUEUE` can create repeated delivery loops; use it only in a bounded test.
`NACK_DISCARD` is destructive and must be limited to a message uniquely owned by the test.

#### JMS

```yaml
taf:
  messaging:
    jms:
      enabled: true
      controllers:
        orders-jms:
          client-id: ${JMS_CLIENT_ID:taf-orders}
          maximum-evidence-records: 1000
```

The JMS adapter deliberately does not define a vendor URL, username, password, or TLS model. Supply
an application-owned `JmsConnectionFactoryProvider` bean backed by the approved vendor's
`jakarta.jms.ConnectionFactory`. The following wiring is **speculative consumer composition**; its
vendor constructor and secret handling must be replaced and verified:

```java
@Bean
JmsConnectionFactoryProvider jmsConnectionFactoryProvider(
    ApprovedJmsConnectionFactory factory) {
  return (controllerName, settings, controllerContext) ->
      factory.connectionFactoryFor(controllerName, settings, controllerContext);
}
```

Use the provider-neutral controller API:

```java
JmsController jms = controller(JmsController.class, "orders-jms");
JmsDestination queue = JmsDestination.queue("orders.completed.test");
Correlation correlation = new Correlation("orderId", orderId);
MessageEnvelope message = new MessageEnvelope(
    orderEventJson().getBytes(StandardCharsets.UTF_8),
    Map.of("content-type", "application/json"),
    Optional.of(correlation));

jms.publish(queue, message);

MessageQuery query = new MessageQuery(
    queue.name(), MessageSelector.correlated(correlation), Duration.ofSeconds(20));
ConsumptionResult consumed = jms.consume(queue, null, query);

Assert.assertEquals(consumed.status(), ConsumptionResult.Status.MATCHED);
```

For a topic, use `JmsDestination.topic(...)`. Durable consumption additionally requires a stable,
authorized client ID and `JmsSubscription`; do not share a durable subscription between parallel
tests unless the broker contract explicitly supports it.

These examples have not been compiled or run as clean consumers. They are intentionally practical
starting points for Codex verification, not claims of end-to-end broker validation.

### 7.8 Service virtualization with WireMock

> **Unverified placeholder:** This section is presentation guidance, not a compile- or
> runtime-verified consumer workflow. Its released WireMock mapping calls show the intended Java
> shape, while `provisionedWireMockResource()` deliberately stands in for project-specific
> environment composition.

Provision a `wiremock` environment through `SessionEnvironmentManager`, then create a session-owned
mapping scope with `WireMockVirtualizationFactory`. Consume the returned
`taf.virtualization.wiremock.base-url`; the same test code can use external or container mode.

Enable the capability and, if authorized, its Testcontainers provider:

```yaml
taf:
  virtualization:
    wiremock:
      enabled: true
      container-enabled: true
      network-faults-enabled: false
```

The exact `taf.virtualization.wiremock.*` properties above are API-verified. Environment selection,
external base URL, container limits, and readiness configuration belong to the consumer's
`taf-environments` composition and may differ by project.

Once `SessionEnvironmentManager` has provisioned or selected the resource, create a session-owned
mapping scope:

```java
EnvironmentResource wireMockResource = provisionedWireMockResource();
WireMockVirtualization virtualization = wireMockFactory.create(
    testSession(), wireMockResource, "integration");

MappingHandle mapping = virtualization.add(
    new VirtualMapping(
        "GET",
        "/customers/42",
        200,
        Map.of("Content-Type", "application/json"),
        "{\"id\":42,\"status\":\"ACTIVE\"}",
        FaultProfile.none()));

RestController customerApi = controller(RestController.class, "customer-api");
customerApi.execute(
        RestRequest.request(Method.GET, mapping.endpoint().toString()).build())
    .assertStatus(200)
    .assertBodyPath("status", "ACTIVE");

virtualization.verify("GET", "/customers/42", 1);
```

`WireMockVirtualizationFactory.create`, `VirtualMapping`, `MappingHandle`, `endpoint`, and `verify`
are API-verified. `provisionedWireMockResource()` is an explicit **speculative placeholder** for the
consumer's environment-manager call, and the REST request may need a relative path or a dedicated
controller whose base URL is the returned WireMock endpoint. Codex must align this composition with
the exact environment and REST APIs.

TAF prefixes paths with a sanitized session namespace and removes that session's mappings and
request journal on close. Latency simulation is supported. Network connection faults are denied by
default and always denied for `prod`/`production`.

For deterministic delay, use a `FaultProfile.Latency` value supported by the staged version.
Network faults require both `network-faults-enabled: true` and a non-production environment. Do not
enable them as a production default. This example has not been compiled or run as a clean consumer.

### 7.9 OpenAPI, AsyncAPI, and consumer contracts

`taf-contracts` performs bounded structural validation of OpenAPI 3.x and AsyncAPI 2.x/3.x JSON or
YAML. It can return in-memory, versioned Java wrapper assets around existing `RestController` and
`MessagingController` APIs; it never sends requests, publishes messages, or writes generated files.
The caller owns any authorized write of returned scaffold content.

Full semantic validation is an extension point through `ContractAdapter`. Consumer-contract support
is **not available** until a provider is approved; `UnsupportedConsumerContractAdapter` returns a
structured capability gap and does not imply Pact support.

No clean OpenAPI/AsyncAPI consumer example is released. Validate the contract API against your
staged version before adding a build gate.

### 7.10 Environment composition and provisioning

`taf-environments` supplies typed consumer configuration, named capabilities, preflight
contribution, and environment-provider composition. Provisioning belongs to an
`EnvironmentProvider`; controllers consume ready resources and never provision infrastructure.

Use dynamic ports for containers, explicit readiness, bounded startup, and idempotent cleanup.
Distinguish unavailable/misconfigured environments from product failures. A degraded environment
must follow an explicit test policy.

The base/local/CI profile pattern and `ConsumerPreflight.verify()` are the released user workflow.
A comprehensive external/Testcontainers golden scenario across all providers is not released.

### 7.11 Observability assertions

`taf-observability` is a released optional assertion capability for application telemetry. It is not
an infrastructure provisioning module and is not a substitute for functional assertions.

The release branch does not provide a module README or clean consumer example exposing a coherent
configuration and public workflow. Treat this capability as **partial for onboarding**. Do not
invent metric/log/trace property names or APIs; request the version-matched reference contract and
verify a consumer smoke before using it as a release gate.

### 7.12 Controlled data migration

`taf-data-migration` applies consumer-owned Git-managed Flyway migrations to named JDBC SUT
connections during preflight. It is inert unless explicitly enabled:

```yaml
taf:
  migration:
    enabled: true
    connections:
      orders-db:
        policy: validate-and-migrate
        locations: [classpath:db/migration/orders]
```

Policies are `DISABLED`, `VALIDATE_ONLY`, and `VALIDATE_AND_MIGRATE`. Validation occurs before
migration. Baseline-on-migrate, repair, clean, and destructive reset are always disabled.
Testcontainers targets require an explicit per-connection policy. External/shared targets also
require an application-owned `MigrationAuthorization` bean; properties alone cannot authorize
them. Production is denied by default.

Use migration for deterministic non-production setup, not for per-test business cleanup. This
release has no complete seed/rollback consumer example, and rollback/clean is not implied.

### 7.13 Reporting and Allure

Consumer code uses TAF-neutral reporting annotations; it does not call Allure APIs. The optional
`codinglair-taf-reporting-allure` adapter translates TAF events. Without a reporting adapter,
Runtime supplies a no-op reporter so the same tests remain executable. Controller evidence flows
through `ArtifactCollector` and must be sanitized before adapter publication.

Configure optional single-file Allure publication:

```yaml
taf:
  reporting:
    allure:
      single-file:
        enabled: true
        output-directory: ${TAF_REPORT_OUTPUT:target/taf-reports}
        results-directory: ${TAF_ALLURE_RESULTS:target/allure-results}
        report-name: "Orders Functional Tests"
        timestamp-pattern: yyyyMMddHHmm
        executable: ${TAF_ALLURE_EXECUTABLE:allure}
        timeout: 2m
```

These properties are API-verified. `executable` must identify the approved Allure 2 CLI; do not
download or execute an unapproved binary during a test run.

The released reporting annotations are:

| Annotation | Apply to | Intended report level |
| --- | --- | --- |
| `@TestCaseId` | TestNG test method | Stable external test-case identity |
| `@Workflow` | Public consumer workflow method | Business workflow |
| `@PageAction` | Public page-object method | Web page action |
| `@ComponentAction` | Public page-component method | Reusable UI component action |
| `@ScreenAction` | Public mobile screen method | Mobile screen action |
| `@ApiAction` | Public API object/resource method | Application API action |
| `@BddStep` | Public Cucumber step implementation | Curated BDD step |
| `@Validation` | Public validator/assertion boundary | Business validation |
| `@ControllerAction` | Meaningful public controller/extension operation | Low-level controller diagnostic |

All action annotations take one human-readable `String` value. Put them on meaningful public
boundaries, not private helpers, getters, constructors, or every line of a test. Avoid duplicate
report entries when an annotated high-level method invokes another annotated method for the same
logical action.

Example application-owned hierarchy:

```java
@Component
public class OrderWorkflow {
  private final OrdersPage ordersPage;
  private final OrdersApi ordersApi;

  public OrderWorkflow(OrdersPage ordersPage, OrdersApi ordersApi) {
    this.ordersPage = ordersPage;
    this.ordersApi = ordersApi;
  }

  @Workflow("Submit and verify an order")
  public String submitAndVerify(OrderInput input) {
    String orderId = ordersApi.create(input);
    ordersPage.open(orderId);
    ordersPage.verifyDisplayedOrder(orderId);
    return orderId;
  }
}

@Component
public class OrdersApi {
  @ApiAction("Create order through API")
  public String create(OrderInput input) {
    // Use the invocation-owned named RestController and return the created ID.
    return createOrder(input);
  }
}

@Component
public class OrdersPage {
  @PageAction("Open order details")
  public void open(String orderId) {
    // Use the invocation-owned Playwright page.
  }

  @Validation("Verify displayed order identifier")
  public void verifyDisplayedOrder(String expectedOrderId) {
    Assert.assertEquals(displayedOrderId(), expectedOrderId);
  }
}

@Test
@TestCaseId("ORDER-F2B-001")
public void orderIsVisibleAfterCreation() {
  String orderId = orderWorkflow.submitAndVerify(testInput(OrderInput.class));
  Assert.assertNotNull(orderId);
}
```

The annotation names and `value` contracts are API-verified. Helper methods and application models
in the excerpt are placeholders. Reporting interception is designed for Spring-managed public
objects; direct `new` construction, private methods, and self-invocation may bypass the proxy and
must be checked during consumer verification. `@ControllerAction` is normally already present on
TAF controller operations; consumer pages and workflows should not add a duplicate controller-level
step around the same call.

The golden project writes Allure results to `target/allure-results` and single-file HTML reports
under `target/taf-reports/<timestamp>/` when the approved Allure 2 CLI or
`TAF_ALLURE_EXECUTABLE` is available. See
[`single-file Allure publishing`](reporting/single-file-allure.md) for the version-matched command
and runner-image options.

Publishing or emailing reports is not part of Runtime execution. Verify the expected hierarchy—test
or scenario, workflow/BDD step, page/component/screen/API action, controller diagnostic, then
validation—and ensure each logical action appears once. Verify reports with a secret canary before
enabling artifact publication, and apply the organization's retention/access policy.

### 7.14 Consumer conformance

`taf-consumer-conformance` performs structural validation of generated, migrated, and golden
projects. Consumer build tests can call:

```java
new ConsumerProjectValidator().validate(projectRoot).throwIfInvalid();
```

The CLI accepts one unpacked consumer-project directory and exits `2` for conformance violations.
It checks project structure, lifecycle ownership, session-bound static fields, selector ownership,
prohibited Allure coupling, profiles/suites/test data, and report shape. It does **not** execute an
environment or replace Spring context, preflight, lifecycle, or capability smoke tests.

## 8. Complete front-to-back pattern

A genuine F2B test follows one uniquely owned business identifier and one session trace ID across
layers. Use one framework-owned session and keep application cleanup separate from controller
resource cleanup.

The recommended pattern is:

1. load typed input and expected output by test-case ID;
2. resolve secret references only when the owning client needs them;
3. create a uniquely identifiable order through the owning REST API;
4. extract `orderId` and retain the session trace ID;
5. locate/complete the corresponding UI workflow and assert the same `orderId`;
6. poll REST and the read-only database until both report the expected terminal status;
7. assert persisted business values and any authorized file/message/telemetry side effects; and
8. in `finally`, delete only the data created by this test through its owning application API.

API-verified excerpt:

```java
@Test
@TestCaseId("F2B-ORDER-001")
public void orderCompletesAcrossUiApiAndDatabase() {
  RestController api = controller(RestController.class, "orders-api");
  DatabaseController db = controller(DatabaseController.class, "orders-db");
  String traceId = testSession().getCorrelationContext().getTraceId();
  String orderId = null;
  Throwable primaryFailure = null;

  try {
    RestResponse created = api.execute(
        RestRequest.request(Method.POST, "/orders")
            .header("X-Correlation-Id", traceId)
            .body(orderRequestJson(), "application/json")
            .build())
        .assertStatus(201);

    orderId = created.nativeResponse().jsonPath().getString("id");
    Assert.assertNotNull(orderId, "Create response must contain an order id");

    ordersPage.open(orderId);
    Assert.assertEquals(ordersPage.orderId(), orderId);

    String observedOrderId = orderId;
    AwaitableAssertion.AwaitableAssertionResult completed =
        AwaitableAssertion.create(
                "API and database agree on COMPLETE",
                ignored -> {
                  RestResponse observed = api.execute(
                      RestRequest.request(Method.GET, "/orders/{id}")
                          .pathParameter("id", observedOrderId)
                          .build());
                  if (observed.statusCode() != 200) return false;
                  QueryResult persisted = db.query(
                      "select status from orders where order_id = ?", observedOrderId);
                  return "COMPLETE".equals(
                          observed.nativeResponse().jsonPath().getString("status"))
                      && persisted.rowCount() == 1
                      && "COMPLETE".equals(
                          persisted.rows().getFirst().get("status"));
                },
                Duration.ofSeconds(60),
                Duration.ofSeconds(1))
            .await();
    Assert.assertTrue(completed.isSuccessful(), completed.getMessage());
  } catch (RuntimeException | AssertionError failure) {
    primaryFailure = failure;
    throw failure;
  } finally {
    if (orderId != null) {
      try {
        api.execute(RestRequest.request(Method.DELETE, "/orders/{id}")
            .pathParameter("id", orderId)
            .build()).assertStatus(204);
      } catch (RuntimeException | AssertionError cleanupFailure) {
        if (primaryFailure != null) primaryFailure.addSuppressed(cleanupFailure);
        else throw cleanupFailure;
      }
    }
  }
}
```

`ordersPage` and `orderRequestJson()` are application-owned placeholders. Adapt selectors, request
bodies, paths, status codes, schema, and cleanup to the SUT. The released `AwaitableAssertion`
re-evaluates the predicate until both observed layers report `COMPLETE` or the 60-second deadline
expires; false HTTP/database observations are retried. An exception produces a failed result, while
an `AssertionError` escapes, so the polling predicate intentionally observes without intermediate
assertions. The final assertion reports timeout/failure diagnostics, including the last safe
observation. Consumer code does not call `Thread.sleep`. The `finally` block attempts only owned-data
cleanup, suppresses cleanup failure onto an existing primary failure, and otherwise lets cleanup
failure fail the test normally.

Do not delete by a broad query, purge a shared topic/queue, or clean data that the test did not
create. Closing `TestSession` releases browsers, drivers, connections, consumers, evidence, and
other framework resources; it does not imply SUT business cleanup.

## 9. MCP-assisted workflows

### 9.1 When to use MCP

Use MCP after the Java project exists and works through Maven. An MCP client may help validate the
project, submit controlled compilation/build/test work, inspect status, retrieve sanitized bounded
resources, request report summaries, and cancel work. Generated prompt output remains untrusted and
must be reviewed.

STDIO and authenticated Streamable HTTP expose the same governed contract. STDIO writes protocol
frames only to stdout; server logs go to stderr. HTTP requires the configured OIDC/OAuth audience,
scope/RBAC, project, environment, and approval policy. Never disable these controls to simplify a
client connection.

### 9.2 Tool operations and request fields

The accepted workflow operations in the checked release source are:

| Operation | Behavior | Maximum requested timeout |
| --- | --- | ---: |
| `validate` | Synchronous project validation | 120 seconds |
| `compile` | Asynchronous controlled worker job | 3,600 seconds |
| `build` | Asynchronous controlled worker job | 7,200 seconds |
| `execute` | Asynchronous controlled test job | 86,400 seconds |
| `cancel` | Idempotent job cancellation request | 120 seconds |

An asynchronous request includes a bounded `requestId`, `projectId`, `environment`, approved
workspace, optional selector, positive timeout, and `idempotencyKey`. Add `approvalId` when policy
requires it. Cancellation adds the target job ID. Identifiers must be non-secret bounded tokens;
selectors accept only the server's restricted character set. The server rejects workspaces outside
its approved root, symlink workspaces, overlong timeouts, sensitive-looking request data, and
unauthorized actions.

Do not send environment variables, tokens, database passwords, secret-reference payloads, raw test
data, or arbitrary command lines in an MCP request. The worker resolves only approved execution
configuration at its boundary.

### 9.3 Validate, execute, monitor, retrieve, and cancel

Use the MCP client's normal tool/resource UI rather than hand-authoring protocol frames:

1. Discover server tools, resources, prompts, and versioned schemas.
2. Invoke `validate` for the authorized project/workspace/environment.
3. Correct consumer-project findings in source control; MCP validation does not silently fix them.
4. Invoke `compile` or `build`; then invoke `execute` with a suite/class/tag selector supported by
   the project.
5. Retain the returned `taf://job/<id>` reference. Poll the corresponding bounded job resource until
   it reaches a terminal state; do not busy-loop.
6. Retrieve only authorized result/evidence/report references. Follow pagination and size limits.
7. If work is no longer useful, invoke `cancel` with a new request ID and the target job ID. Repeated
   cancellation is safe; terminal job states remain terminal.

The server-side resource catalog provides bounded, sanitized capability, configuration,
environment-readiness, documentation, evidence-metadata, and job/report resources according to
authorization. Large/binary artifacts stay behind controlled references. Evidence access is scoped
to the caller/project/environment and does not expose resolved secrets.

Scaffolding classes exist in the server module, and the broader contract inventory describes
discover/scaffold/inspect/retrieve/report workflows. However, the checked workflow tool's public
discovery list exposes only `validate`, `compile`, `build`, `execute`, and `cancel`. Treat direct MCP
scaffolding as **not established for this quick start** until the deployed server advertises a
versioned scaffold tool. Never assume a server-side class is automatically an MCP operation.

### 9.4 MCP failure handling

- `APPROVAL_REQUIRED`: obtain an authorized approval; do not reuse or fabricate another approval.
- authorization denied: confirm identity, project, environment, permission, and HTTP audience/scope.
- input rejected: remove secrets, fix bounded identifiers/selector/timeout, and use an approved
  workspace.
- accepted job appears stuck: inspect job progress and sanitized worker diagnostics; cancel if the
  business deadline has passed.
- evidence unavailable: verify job ownership, project/environment scope, retention, and reference;
  do not request filesystem access as a workaround.

## 10. Evidence, reports, cleanup, and parallel execution

### 10.1 Evidence rules

Evidence should answer what ran, where, against which safe identifier, what was observed, and why it
passed or failed. It must exclude credentials, raw authorization headers, secret-reference payloads,
sensitive database fields, unrestricted bodies, physical secret paths, and personal data not
required for diagnosis.

Visual artifacts are opt-in. Sanitize REST/SOAP exchange artifacts and database/file differences
before publication. Store large or binary content by controlled reference. Validate artifact
retention and access in both local and CI profiles.

### 10.2 Cleanup ownership

- `TestSession` closes invocation-owned framework resources in deterministic reverse order.
- the environment provider cleans resources it provisioned, according to its lifecycle policy;
- WireMock removes only the session namespace;
- Appium removes only packaged applications installed by controller operations, when configured;
- external databases receive no implicit cleanup;
- the test/application workflow deletes only uniquely owned SUT data through an authorized path.

Cleanup must be idempotent and run after failures and cancellation. Preserve the primary test
failure and report cleanup problems separately.

### 10.3 Parallel execution

Each TestNG method or Cucumber scenario must own its session, controller instances, browser page,
mobile driver, message consumers, correlation values, mutable test data, and cleanup identifiers.
Do not use static/global session-bound state.

Before enabling parallelism:

- generate unique business and correlation IDs;
- use isolated test users or prove they are concurrency-safe;
- isolate browser contexts, devices, database rows, broker groups/destinations, files, and WireMock
  namespaces;
- ensure test-definition reads are immutable and repository writes use version checks;
- set bounded worker/container/device concurrency; and
- run a repeated parallel cleanup smoke that includes failures and cancellations.

One Android device normally cannot safely execute several independent UI sessions concurrently.
Scale devices/providers instead of sharing a driver.

## 11. Local and CI execution

### 11.1 Local commands

From the consumer project:

```powershell
.\mvnw.cmd -DskipTests test-compile
.\mvnw.cmd test
.\mvnw.cmd -Pfunctional-suite test
.\mvnw.cmd -Pbdd-suite test
```

```bash
./mvnw -DskipTests test-compile
./mvnw test
./mvnw -Pfunctional-suite test
./mvnw -Pbdd-suite test
```

Run browser, REST, SOAP, database, messaging, Appium, migration, and complete F2B profiles only when
the required authorized environment is available. Do not run framework module test commands from a
consumer project.

### 11.2 CI pattern

A consumer CI job should:

1. check out the consumer project;
2. select Java 25;
3. use the checked-in Maven Wrapper and approved Maven settings/mirror;
4. inject non-secret configuration and bind secrets through the CI secret facility;
5. select `application-ci.yaml` and an explicit environment/suite;
6. run preflight, compilation, deterministic tests, and only the authorized live capability gates;
7. always run framework/environment/application cleanup; and
8. scan and publish only sanitized results/evidence/reports under the retention policy.

Example Maven step:

```powershell
$env:SPRING_PROFILES_ACTIVE = "ci"
$env:TAF_ENVIRONMENT = "integration"
.\mvnw.cmd --batch-mode --no-transfer-progress -Pfunctional-suite verify
```

```bash
export SPRING_PROFILES_ACTIVE=ci
export TAF_ENVIRONMENT=integration
./mvnw --batch-mode --no-transfer-progress -Pfunctional-suite verify
```

Do not place secret values in workflow YAML, Maven arguments, build logs, caches, container images,
or uploaded artifacts. Jenkins and GitLab reference pipelines may call the same Maven gates, but the
consumer project's approved CI policy remains authoritative.

## 12. Troubleshooting user projects

### Wrapper or dependency problems

- **Wrapper files missing:** copy `mvnw`, `mvnw.cmd`, and `.mvn/wrapper` together.
- **TAF artifact not found:** confirm the release-approved repository and Maven settings. Do not add
  an arbitrary repository to make the error disappear.
- **Wrong Java version:** check `java -version` and the JDK used by the IDE/CI runner; both must use
  Java 25.
- **Unexpected version conflict:** remove independent versions for BOM-managed TAF artifacts and
  compare with the approved consumer POM.

### Configuration and lifecycle problems

- **Preflight rejects configuration:** replace all required placeholders, select compatible named
  capabilities, and make secret aliases available. Do not add committed fallback credentials.
- **Controller not found:** verify the dependency, `enabled: true`, active profile, controller type,
  and exact configured name.
- **No active session:** call session-aware factories only inside `TafBaseTest` or the framework
  Cucumber scenario lifecycle. Do not open hidden sessions.
- **Duplicate sessions or steps:** remove custom Cucumber hooks, manual session lifecycle, duplicate
  reporters, and direct Allure calls.

### SUT interaction problems

- **REST/SOAP 401 or 403:** verify the authorized test identity, secret reference, audience, and
  application permission. Inspect sanitized evidence, never raw tokens.
- **Database connection fails:** verify the JDBC driver, URL, network policy, readiness, access mode,
  and secret provider. Do not print connection properties.
- **Write/setup/cleanup rejected:** confirm the connection is intentionally read-write and has the
  required explicit authorization. Do not weaken a read-only policy to pass a validation test.
- **Polling times out:** inspect the last sanitized observation and correlation ID; confirm the
  business deadline and cross-layer identifier. Do not add unbounded retries or arbitrary sleeps.
- **Locator fails:** inspect an authorized trace/screenshot and current DOM, then update the owning
  page/component locator specification.
- **Appium cannot connect:** start/provision Appium, UiAutomator2, and the authorized device outside
  the controller; confirm the exact endpoint/device ID.
- **Message not observed:** verify destination, correlation key, consumer-group isolation, offset/
  acknowledgement policy, and bounded timeout; do not purge a shared destination.
- **File rejected:** verify the named sandbox root, real path, symlinks, size bound, encoding,
  delimiter/layout, checksum, and media type.

### Evidence and cleanup problems

- **Sensitive value appears in output:** stop publishing, revoke/rotate affected credentials as
  required, correct redaction/classification, remove exposed artifacts according to policy, then
  rerun with a canary.
- **Report missing:** confirm the reporting adapter, results directory, approved Allure executable,
  and profile. Test success does not guarantee a publisher was configured.
- **Cleanup fails:** preserve the original failure, identify the resource owner, retry only an
  idempotent authorized cleanup, and never broaden a deletion query.
- **Parallel-only failures:** look for static session/controller/page/data state, shared users,
  duplicate IDs, fixed ports, shared files, broker groups, or device reuse.

## 13. Release limitations and unavailable features

The following boundaries are intentional or incomplete in this release:

| Capability | Release status for consumers |
| --- | --- |
| Runtime Core, TestNG lifecycle | Released; golden consumer pattern available |
| Cucumber lifecycle | Released; framework hooks and golden runner pattern available |
| Playwright | Released and opt-in; maintained SauceDemo reference available |
| REST and database | Released; corrected DOC-001 examples may still be uncommitted and require SUT adaptation |
| SOAP | Released; no complete clean-consumer fault/auth example; advanced WS-* features listed above unavailable |
| Files | Released; bounded public capability, but no golden consumer workflow |
| Android Appium | Released for native Android; configuration and API-verified excerpt included; external Appium/device required; no iOS/cloud provider |
| Kafka, RabbitMQ, JMS | Released adapters; configuration/API excerpts included but not yet clean-consumer compiled or live-broker verified |
| WireMock | Released provider; mapping API excerpt included, while environment-resource composition remains speculative pending verification |
| OpenAPI/AsyncAPI | Released structural validation/scaffolding; no golden consumer workflow |
| Consumer contracts | Provider SPI only; no approved Pact or other provider |
| Test definitions | CSV and JSON/YAML released; MongoDB optional; YAML negative onboarding example remains a gap |
| Local secrets | Environment/Jasypt released; no Vault/cloud/Kubernetes/workload-identity provider |
| Environments | Released composition/preflight; comprehensive provider golden scenario absent |
| Observability | BOM-managed/released, but no coherent public onboarding workflow in the assessed repository |
| Data migration | Released for controlled non-production setup; no destructive reset/clean or general rollback workflow |
| Allure reporting | Released adapter; publication requires approved external Allure execution path |
| Consumer conformance | Released structural gate; does not run the SUT |
| MCP | Governed validate/compile/build/execute/cancel slice and bounded resources released; no raw capability tools |
| MCP scaffold | Server-side classes exist, but the checked tool discovery surface does not establish it as a callable operation |
| Quality Intelligence / QA Agent | Not available in the community release |
| AI-system testing controller | Not available |
| Comprehensive all-capability golden consumer | Not available |

When a released artifact lacks a coherent public workflow, treat that as a follow-up documentation
and release-readiness gap—not permission to infer APIs from names. Add the capability only after
checking the exact staged public contract and proving a narrow consumer compile and smoke in an
authorized environment.

# Consumer Project Blueprint

**Blueprint version:** 1.0  
**Status:** Approved SAD v1.8 baseline for CP-003 and the clean DEMO-001 implementation
**Descriptor schema:** [`schemas/consumer-project-blueprint-v1.schema.json`](schemas/consumer-project-blueprint-v1.schema.json)

## 1. Purpose and authority

This blueprint turns SAD v1.8 sections 8.5–8.6 and ADR-019/ADR-020 into an implementation target for official scaffolding, generated projects, migrations, and golden examples. The descriptor selects a supported shape; it does not replace `application*.yaml`, contain environment values, or contain secrets.

The version is the compatibility boundary. A change that removes or reinterprets a descriptor value or a public extension point requires a new schema version, compatibility review, and migration guidance. Additive capability-manifest growth that remains valid under version 1 is not by itself a blueprint break.

## 2. Ownership and dependency rules

| Layer | Owns | Must not own |
|---|---|---|
| TAF Runtime and runner modules | `TestSession`, typed/named lazy controllers, configuration and test-definition SPIs, lifecycle/cleanup, artifacts, sanitization, reporter-neutral events, `TafBaseTest`, `TafCucumberHooks`, and the Cucumber-TestNG base runner | SauceDemo pages, business workflows, assertions, consumer configuration, or test data |
| Consumer automation abstractions | Pages/components (POM/PCOM), API resources (AOM), screens, messaging workflows, repositories, services, models, validators, factories, customer Spring configuration, and third-party beans | Session creation/closure, copied runner hooks, controller implementations, reporter-vendor calls, or infrastructure provisioning |
| Executable tests | Scenario intent, orchestration, unique traceability identifiers, and final assertions | Browser selectors/transport plumbing, lifecycle management, or reusable business workflows |

Dependencies flow `test -> workflow/service -> page/component or other capability object -> public TAF controller`. Validators consume typed actual/expected models. Consumer objects call controllers; they never construct controllers or cache invocation-bound resources in singleton beans. Spring may compose stateless factories and configuration, while mutable session-bound objects resolve their resource from the active session for each invocation.

## 3. Default project model

- Java 25 and the repository-supported Spring Boot 4.x line.
- A conventional `@SpringBootApplication`, non-web by default.
- Only selected capability starters and runner modules. Unselected adapters and vendor libraries must be absent.
- Typed configuration for every named controller instance, with `application.yaml`, `application-local.yaml`, and `application-ci.yaml`.
- Required environment-specific values use explicit `REPLACE_ME` placeholders or secret-reference aliases. Generation may compile; environment execution must fail preflight with one aggregated diagnostic listing capability, field, environment, and corrective action for every unresolved value.
- One authoritative test-definition provider per project.
- Ordinary TestNG methods extend framework-owned `TafBaseTest`. BDD uses a thin consumer runner extending the framework Cucumber-TestNG runner and registers framework hook glue. The two audiences use separate TestNG XML suites.
- Consumer code uses only reporter-neutral TAF annotations/contracts. Allure types and lifecycle calls remain in the reporting adapter.

Example descriptor:

```json
{
  "schemaVersion": "1.0",
  "project": {
    "name": "playwright-sauce-demo",
    "type": "focused-golden",
    "basePackage": "com.codinglair.taf.demo.sauce"
  },
  "runtime": { "version": "${taf.version}" },
  "capabilities": ["web-playwright"],
  "runners": ["testng", "cucumber-testng"],
  "testDefinitions": { "provider": "file-csv", "authoritative": true },
  "reporting": { "adapter": "allure" }
}
```

The descriptor belongs at `src/test/resources/taf-project.json`. It contains selection metadata only and no URL, credential, token, secret value, filesystem location, or controller instance configuration.

## 4. Capability-oriented package rules

Packages are conventional rather than rigid, but ownership and dependency direction are mandatory. Create only packages needed by selected capabilities.

| Concern | Conventional package | Rule |
|---|---|---|
| Spring application | project root package | One bootstrap class; no test lifecycle logic |
| Customer configuration | `config` | `@Configuration`, `@ConfigurationProperties`, factories/adapters, third-party beans; no copied auto-configuration |
| Models | `model` | Typed input, expected, actual, and domain values; no controller or runner dependency |
| Web POM | `page` | One cohesive page responsibility; wraps Playwright-controller operations |
| Web PCOM | `component` | Reusable/complex page regions, used by pages or workflows |
| API AOM | `api` | Cohesive resource/endpoint group with typed requests/responses |
| Mobile | `screen`, `component` | Screen objects and reusable mobile components |
| Messaging | `messaging`, `workflow` | Typed events and business-oriented publish/consume flows |
| Persistence | `repository` | Application-specific queries/mapping through TAF database controllers |
| Services/workflows | `service`, `workflow` | Cross-object business operations; no assertions or lifecycle ownership |
| Validation | `validation` | Expected-versus-actual comparison and actionable assertion messages |
| Functional tests | `tests.functional` | Technical verification, `@TestCaseId`, orchestration and assertions |
| BDD | `bdd.runner`, `bdd.steps` | Thin runner and curated business step definitions; step classes delegate to workflows/objects |

POM and PCOM classes remain single-purpose. A login page cannot absorb catalog, product-detail, cart, or checkout behavior. Spring injection may remove repetitive factory wiring, but it must not turn session-bound pages into singleton state holders.

## 5. SauceDemo target tree

```text
demos/playwright-sauce-demo/
├── pom.xml
├── src/main/java/com/codinglair/taf/demo/sauce/
│   ├── SauceDemoApplication.java
│   ├── config/SauceDemoConfiguration.java
│   ├── model/{WebUser,Product}.java
│   ├── page/{LoginPage,ProductsPage,ProductDetailsPage,CartPage,CheckoutPage}.java
│   ├── component/{HeaderComponent,InventoryItemComponent}.java
│   ├── service/LoginService.java
│   ├── workflow/PurchaseWorkflow.java
│   └── validation/{StringValidator,ProductValidator}.java
└── src/test/
    ├── java/com/codinglair/taf/demo/sauce/
    │   ├── tests/functional/{SauceDemoLoginTest,SauceDemoProductTest}.java
    │   ├── bdd/runner/SauceDemoCucumberTest.java
    │   ├── bdd/steps/{LoginSteps,PurchaseSteps}.java
    │   └── architecture/SauceDemoArchitectureTest.java
    └── resources/
        ├── taf-project.json
        ├── application.yaml
        ├── application-local.yaml
        ├── application-ci.yaml
        ├── data/{inputs.csv,expected-outputs.csv}
        ├── features/purchase-happy-path.feature
        ├── testng-functional.xml
        └── testng-cucumber.xml
```

Production sources contain reusable consumer abstractions only. Executable tests, runner glue, features, suites, and test data are test sources/resources. Names may vary, but every shown responsibility must remain distinct when implemented.

## 6. Lifecycle, data, configuration, and reporting contracts

Each TestNG method receives exactly one session through `TafBaseTest`; each Cucumber scenario receives exactly one through `TafCucumberHooks`. TestNG-visible Cucumber wrapper invocations and listeners create no session. Cleanup is idempotent, reverse ordered, and occurs after success, failure, cancellation, and partial initialization. Parallel invocations share no mutable session, page, context, or controller state.

`@TestCaseId` or its Cucumber tag resolves a unique definition in the configured provider. For SauceDemo v1, CSV inputs and expected outputs are resources and remain the authoritative source until an approved provider migration. Models are typed; tests do not parse CSV directly. Missing, duplicate, malformed, or unmatched IDs fail with actionable diagnostics.

Configuration declares the named Playwright controller and externalized base URL, browser, timeout, headless mode, report output reference, and any secret-reference aliases. URLs and secret values are never hard-coded. Consumers may configure public factories/extension beans, but controller provisioning and lifecycle remain framework-owned.

The required report hierarchy is:

```text
execution
└── TestNG method or Cucumber scenario [test-case ID, requirement tags]
    ├── business workflow / BDD step
    │   ├── page or component action
    │   │   └── controller action (when independently meaningful)
    │   └── validation [expected versus sanitized actual]
    └── failure evidence [sanitized artifact references]
```

Every meaningful page action, BDD step, controller action, and validation is visible, but nested calls are duplicate-suppressed. High-level steps do not cause their annotated lower-level plumbing to appear twice. Failure evidence flows through `ArtifactCollector`; report content, screenshots, configuration, and errors are sanitized before publication.

## 7. Conformance and acceptance

CP-002 must automate these rules. Until then, reviewers must verify descriptor/schema validity, selected/unselected dependencies, package direction, no copied lifecycle/controller code, no direct Allure use, profile context loading, aggregated preflight, unique traceability, CSV resolution, session isolation and cleanup, report hierarchy and duplicate suppression, sanitization, and representative controlled execution. Generated or migrated output remains a proposed working-tree change until all applicable gates pass and a human approves it.

If a capability manifest or blueprint cannot express a requirement, implementation stops with a framework/blueprint gap; consumer code must not fabricate the missing Runtime capability.

## 8. SAD v1.8 Playwright template and migration

The executable blueprint is `blueprints/playwright-consumer-v1`. Its generator emits the base
Playwright/TestNG/CSV shape and selects Cucumber-TestNG and Allure only when requested. Blueprint
and descriptor schema version 1.0 remain unchanged because the SAD v1.8 correction is additive and
does not remove or reinterpret descriptor fields.

Consumers migrating from the pre-v1.8 example must remove consumer-owned session, preflight, and
reporting wrappers; replace generic service lookup and direct construction with Spring injection;
move immutable named `LocatorSpec` declarations into their POM/PCOM owner; keep annotated consumer
beans proxyable; and choose workflows only for reusable prerequisites while leaving the actual
subject-under-test actions explicit. `TafBaseTest`, `TafCucumberHooks`, neutral reporting, and
controller cleanup remain framework-owned.

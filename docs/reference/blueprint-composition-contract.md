# Blueprint composition contract 1.0

This document freezes the Gate A contract consumed by later scaffold composition and MCP work. It
does not implement generation. The request and contribution schemas are
[`scaffold-request-v1.schema.json`](../architecture/schemas/scaffold-request-v1.schema.json) and
[`blueprint-contribution-v1.schema.json`](../architecture/schemas/blueprint-contribution-v1.schema.json).
The starter capability manifest remains the sole authority for dependency topology; a blueprint
contribution names a manifest capability/provider ID and never reproduces implementation-module
dependencies.

## Value model and normalization

An input request has the value shape represented by the request schema. Normalization is pure and
uses these steps in order:

1. Trim scalar strings; reject empty values. Validate `groupId`, `artifactId`, `basePackage`, and a
   literal `tafVersion`; normalize selection tokens with locale-independent uppercase.
2. De-duplicate capabilities and sort them by enum order `API`, `DATABASE`, `MESSAGING`, `MOBILE`,
   `WEB`. A duplicate with different option values is a conflict, not a last-write-wins update.
3. Require exactly one messaging provider when `MESSAGING` is selected and reject a provider when
   it is not selected. Other capability provider choices are not supported in version 1.0.
4. When `MOBILE` is selected and platform is omitted, materialize
   `mobile={platform:ANDROID,automationName:UIAUTOMATOR2}`. If Android is explicit and automation
   name is omitted, materialize `UIAUTOMATOR2`. Reject explicit `IOS` as `SCF_UNSUPPORTED_IOS`;
   the schema admits the token so validation can return that actionable domain error.
5. Default omitted runner, reporting, and test-definition selections to `TESTNG`, `ALLURE`, and
   `FILE_CSV`. Emit every default in the normalized request. Sort all object keys lexicographically
   when serializing the normalized form.

After normalization, the request must satisfy the request schema. The same canonical normalized
request and blueprint version select the same contributions and produce byte-identical content,
path set, file modes, and line endings, except for the allowed metadata below.

## Selection, ordering, and plan

Contributions are immutable values with a stable ID, kind, selector, integer order, starter
manifest references, owned paths, and owned configuration pointers. Select exactly one common
contribution, then matching capability, provider, runner, and reporting contributions. Sort the
complete set by `(order ascending, id Unicode code-point ascending)`. Version 1.0 reserves these
order bands: common `100–199`, capability `200–299`, provider `300–399`, runner `400–499`, and
reporting `500–599`. Duplicate `(order,id)` pairs and IDs are invalid.

Planning renders every source to an in-memory `CompositionPlan` containing normalized request,
blueprint version, selected contribution IDs, dependency manifest IDs, target paths, configuration
operations, content digests, and all diagnostics. Path substitution occurs during planning. A
target path must be relative, normalized with `/`, remain below the destination, contain no empty,
`.` or `..` segment, and compare using its exact normalized spelling. Implementations must also
reject case-folded aliases on case-insensitive targets.

No destination directory or file may be created, truncated, moved, or deleted until the complete
plan has passed schema, selection, compatibility, path, ownership, collision, and unresolved-token
validation. Existing-target checks are part of this same read-only phase. Any error returns the
whole ordered diagnostic list and an empty write set.

## Ownership and merge rules

- `CREATE` grants exclusive ownership of the complete target. Two contributions, or a contribution
  and an existing target, claiming that path is a collision.
- `MERGE` is permitted only for declared structured documents. Every contributor must declare a
  JSON Pointer and merge rule. Undeclared nodes and text/line-based merge are forbidden.
- `REQUIRE_EQUAL` accepts identical canonical values only. `SET_IF_ABSENT` fails if a different
  value exists. `DEEP_MERGE_NO_OVERWRITE` recursively adds absent object keys and fails on a
  different scalar/array value. `APPEND_UNIQUE_BY_ID` appends objects in contribution order and
  fails when an existing ID has different canonical content.
- An owned pointer covers its value and descendants. Equal pointers or ancestor/descendant claims
  collide unless all claims use `REQUIRE_EQUAL` and resolve to the same canonical value.
- Maven dependency composition is a special structured merge by coordinate
  `(groupId,artifactId,type,classifier)`. It is populated only by resolving the selected starter
  manifest IDs. Same-coordinate version/scope/exclusion differences are conflicts.

## Diagnostics

Each error is a value `{code, phase, contributionId?, target?, pointer?, message, correctiveAction}`.
Fields are bounded, sanitized, contain no secret values, and diagnostics sort by
`(phase, target, pointer, code, contributionId)` with absent values as empty strings.

| Code | Trigger | Corrective action example |
| --- | --- | --- |
| `SCF_INVALID_REQUEST` | Schema/name/version failure | Correct the named field and resubmit. |
| `SCF_INCOMPLETE_SELECTION` | Messaging has no provider | Select one of AWS, JMS, KAFKA, or RABBITMQ. |
| `SCF_UNSUPPORTED_SELECTION` | Unknown capability/provider/runner/reporting combination | Choose a combination in the release capability matrix. |
| `SCF_UNSUPPORTED_IOS` | Mobile platform is explicitly iOS | Select Android/UiAutomator2; iOS is not implemented in 1.2.0. |
| `SCF_MANIFEST_REFERENCE` | Contribution refers to an absent/ambiguous starter ID | Repair the blueprint/manifest version pair; do not synthesize dependencies. |
| `SCF_PATH_INVALID` | Absolute, traversing, empty, unresolved, or escaping path | Supply a safe relative target and valid package/name inputs. |
| `SCF_PATH_COLLISION` | Multiple creates, create/merge conflict, case alias, or existing target | Change ownership or choose an empty destination. |
| `SCF_CONFIG_COLLISION` | Incompatible pointer/value/merge-rule claims | Assign one owner or make the values explicitly equal. |
| `SCF_UNRESOLVED_TOKEN` | Rendered content retains a blueprint placeholder | Provide the missing normalized value or repair the contribution. |

Examples: `MESSAGING` without `providers.messaging` fails before mutation with
`SCF_INCOMPLETE_SELECTION`; `{MOBILE, platform:IOS}` fails with `SCF_UNSUPPORTED_IOS`; common and
Web both creating `src/test/resources/application.yaml` fails with `SCF_PATH_COLLISION`; Web
setting `/taf/web/playwright/enabled=true` while another contribution sets it to `false` fails with
`SCF_CONFIG_COLLISION`. All four return zero writes.

## Generated metadata

Deterministic metadata is allowed: `schemaVersion`, `blueprintVersion`, `tafVersion`, sorted selected
IDs, and SHA-256 content/plan digests. Wall-clock timestamps, random UUIDs, machine/user names,
absolute source/destination paths, environment-dependent line endings, and generator/MCP identity
are forbidden in generated files. An invocation/job ID may appear in an out-of-tree operational
response or audit event, but is excluded from output identity and content.

## Existing Playwright blueprint inventory

The inventory boundary is all 37 versioned files under `blueprints/playwright-consumer-v1` at
contract freeze, plus the intentionally ignored local workspace overlay when it exists. “Move”
means a later contribution assignment relocates or re-renders the asset; this assignment does not
mutate the executable blueprint.

| Existing asset | Classification | Migration disposition |
| --- | --- | --- |
| `generate.ps1` | obsolete | Replace with the SCF-120-002 composition entry point; do not reuse its copy-before-validation flow. |
| `README.md` | Web | Replace with versioned composition usage and retain Playwright-specific guidance under Web. |
| `template/README.md` | Web | Move to the Web example contribution; remove “non-web Playwright” wording. |
| `template/pom.xml` | common | Split common build/profile structure from manifest-resolved starters and runner/reporting merges. |
| `template/src/main/java/__PACKAGE_PATH__/PlaywrightConsumerApplication.java` | common | Rename to an artifact-derived bootstrap and retain in common. |
| `template/src/main/java/__PACKAGE_PATH__/configuration/ConsumerConfiguration.java` | provider | Split named external-resource/test-definition factories into their selected provider contributions. |
| `template/src/main/java/__PACKAGE_PATH__/configuration/ConsumerEnvironmentProperties.java` | common | Retain a capability-neutral environment/secret-reference skeleton. |
| `template/src/main/java/__PACKAGE_PATH__/model/LoginInput.java` | Web | Move to the Web example contribution. |
| `template/src/main/java/__PACKAGE_PATH__/model/ProductExpectation.java` | Web | Move to the Web example contribution. |
| `template/src/main/java/__PACKAGE_PATH__/page/LocatorSpec.java` | reusable capability | Move to the reusable Web object support contribution. |
| `template/src/main/java/__PACKAGE_PATH__/page/LoginPage.java` | Web | Move to the Web example contribution. |
| `template/src/main/java/__PACKAGE_PATH__/page/ProductsPage.java` | Web | Move to the Web example contribution. |
| `template/src/main/java/__PACKAGE_PATH__/component/HeaderComponent.java` | Web | Move to the Web example contribution. |
| `template/src/main/java/__PACKAGE_PATH__/service/LoginWorkflow.java` | Web | Move to the Web example contribution. |
| `template/src/main/java/__PACKAGE_PATH__/validation/ProductValidator.java` | Web | Move to the Web example contribution. |
| `template/src/test/java/__PACKAGE_PATH__/architecture/BlueprintConformanceTest.java` | common | Retain as generated common conformance coverage. |
| `template/src/test/java/__PACKAGE_PATH__/architecture/ContractStructureTest.java` | common | Retain capability-neutral rules; move Web package assertions to Web. |
| `template/src/test/java/__PACKAGE_PATH__/architecture/LifecycleTest.java` | common | Retain TestSession lifecycle conformance. |
| `template/src/test/java/__PACKAGE_PATH__/architecture/ReportingContractTest.java` | runner/reporting | Move to the selected reporting contribution. |
| `template/src/test/java/__PACKAGE_PATH__/context/ContextLoadingTest.java` | common | Retain default-profile context verification. |
| `template/src/test/java/__PACKAGE_PATH__/context/LocalContextTest.java` | common | Retain local-profile context verification. |
| `template/src/test/java/__PACKAGE_PATH__/context/CiContextTest.java` | common | Retain CI-profile context verification. |
| `template/src/test/java/__PACKAGE_PATH__/context/PreflightTest.java` | common | Retain aggregated common preflight; capability contributions add rules. |
| `template/src/test/java/__PACKAGE_PATH__/functional/ProductFunctionalExample.java` | Web | Move to the Web/TestNG example contribution. |
| `template/src/test/resources/application.yaml` | common | Split common Spring/environment/secrets nodes from capability-owned configuration pointers. |
| `template/src/test/resources/application-local.yaml` | provider | Optional, ignored local workspace overlay; split local external-resource/provider overrides by selected provider when present. |
| `template/src/test/resources/application-ci.yaml` | provider | Split CI external-resource/provider overrides by selected provider. |
| `template/src/test/resources/taf-project.json` | common | Generate selection metadata from the normalized request; no MCP dependency or operational values. |
| `template/src/test/resources/testng-functional.xml` | runner/reporting | Move to the TestNG runner contribution. |
| `template/src/test/resources/testng-bdd.xml` | runner/reporting | Move to the Cucumber-TestNG runner contribution. |
| `template/src/test/resources/test-data/inputs.csv` | Web | Move to the Web example contribution. |
| `template/src/test/resources/test-data/expected-outputs.csv` | Web | Move to the Web example contribution. |
| `template/src/test/resources/test-data/TC-LOGIN-SUBJECT.csv` | Web | Move to the Web example contribution. |
| `template/src/test/resources/test-data/TC-PREREQUISITE.csv` | Web | Move to the Web example contribution. |
| `bdd-overlay/src/test/java/__PACKAGE_PATH__/bdd/runner/BusinessBehaviorRunner.java` | runner/reporting | Move to the Cucumber-TestNG runner contribution. |
| `bdd-overlay/src/test/java/__PACKAGE_PATH__/bdd/steps/ProductSteps.java` | Web | Move to the Web+Cucumber example contribution. |
| `bdd-overlay/src/test/resources/features/product.feature` | Web | Move to the Web+Cucumber example contribution. |
| `bdd-overlay/src/test/resources/test-data/TC-BDD.csv` | Web | Move to the Web+Cucumber example contribution. |

### Configuration-node inventory

| Current node(s) | Classification and disposition |
| --- | --- |
| POM parent, compiler release/parameters, Spring Boot base/test libraries, conformance kit | common; retain as common build structure. |
| `taf-web-playwright` dependency | Web; replace with the manifest ID `WEB` resolved to `codinglair-taf-starter-web`. |
| `codinglair-taf-runner-testng`, Surefire, `browser-smoke`, suite path | runner/reporting; TestNG owns the runner/suite and Web owns the browser smoke activation. |
| optional Cucumber dependency/token | runner/reporting; replace text substitution with the Cucumber-TestNG contribution. |
| optional Allure dependency/token | runner/reporting; replace text substitution with the Allure contribution. |
| direct `taf-test-definitions` dependency and CSV resources | provider; starter owns dependency composition and FILE_CSV owns configuration/resources. |
| `spring.main.web-application-type` | common; retain `none`. |
| `consumer.environment.name` and `user-secret-reference` | common; retain typed environment and opaque secret-reference placeholders. |
| `taf.web.playwright.enabled/controllers/shop/{mode,engine,base-url,headless}` | Web; Web owns `/taf/web/playwright`; environment overlays may contribute non-conflicting leaf values. |
| descriptor `schemaVersion`, `project`, `runtime` | common; derive from normalized request. |
| descriptor capability `web-playwright` | Web; emit only when Web is selected. |
| descriptor runner entries and reporting adapter | runner/reporting; emit from normalized selection. |
| descriptor `testDefinitions` | provider; emit the selected authoritative provider. |
| TestNG suite names, test class/package selectors, listeners | runner/reporting; runner owns suite structure, selected capability owns included examples. |
| CSV columns, case IDs, feature tags, and example values | Web; keep only with the representative Web examples and never treat values as generator configuration. |

## Contract separation, compatibility, and ADR conformance

- BR-024/FR-MCP-020: starter POMs and the capability manifest own dependencies; blueprints own
  project files and structured configuration. MCP selects and orchestrates but owns neither graph.
- BR-025/FR-MCP-021: contribution dependencies can resolve only public starter manifest IDs and
  cannot add MCP artifacts. Generated descriptors contain no generator identity.
- FR-SCF-001–005: common is capability-neutral; Web assets above are removed from common and all
  later capabilities use independent contributions rather than inheriting Playwright packages.
- FR-SCF-006/FR-MCP-019: normalization and full read-only plan validation precede mutation and
  yield ordered actionable errors.
- FR-SCF-007 is prepared by manifest references and contribution ownership; concrete examples and
  preflight hooks are delivered by SCF-120-003.
- FR-SCF-008 and ADR-027 are enforced by canonical normalization, stable ordering, declared merges,
  collision detection, bounded metadata, Android defaulting, and explicit unsupported-iOS failure.

This is an additive 1.2.0 contract. It does not alter the published consumer descriptor 1.0 or any
1.1.0 Java/configuration contract. The legacy Playwright generator remains usable until its later
migration; its behavior is inventoried, not reinterpreted here.

# KISS refactoring discovery inventory

Date: 2026-10-03. Assignment: the user-supplied **KISS Refactoring Discovery** request (discovery only). Baseline: `3c945e440449e344536f34361d7c1a48212e76b0`; initial working tree clean.

## Scope, rules and evidence

Reviewed repository Java source, tests, examples, blueprint Java assets and source-launched build utilities. No production/test source, dependencies, public contracts, package locations or Git history were changed. No new architecture, shared framework, generic execution layer or dependency is proposed.

Authority: [AGENTS.md](../../AGENTS.md), [Java/Spring KISS rules](../../.roo/rules/06-java-spring-standards.md), [architecture boundaries](../../.roo/rules/02-architecture-boundaries.md), [public SAD](../architecture/solution-architecture.md), [compatibility baseline](compatibility-matrix.md), [Apple contract inventory](apple-130-contract-and-qualification-plan.md), and context from [MOB-130-002](../assignments/handoffs/MOB-130-002-handoff.md) and [SCF-130-001](../assignments/handoffs/SCF-130-001-handoff.md). The inline discovery assignment declares no dependency assignments; the handoffs clarify existing contracts rather than authorize implementation. They explicitly record that a separate Legacy Contract Compatibility Matrix is absent; existing source contracts and the engineering compatibility baseline apply. The user explicitly requests repository-wide discovery and this output directory, overriding narrower routine assignment-path/report-location rules.

Assessment follows the project rule: cohesive, explainable operations without excessive splitting. A method longer than 20 lines is a screening trigger, not a defect. Simple constructors, declarative catalogs, literal fixtures, linear encoding and necessary resource scopes may remain long. A helper is justified only when it gives a meaningful operation a name or removes duplicated policy; do not introduce one helper per branch or statement.

JDK compiler-tree parsing covered **997 Java files, 5,292 methods/constructors with bodies**, with **643 declarations over 20 lines** (286 classified outside a `src/test/` tree and 357 inside one). Parse-only inspection used the installed Java 25 compiler API without dependencies or type resolution. Generated build trees (`target`), `.git` and `.idea` were excluded. Methods with no bodies, lambdas as standalone declarations, and initializer blocks are not separately counted. Nested/anonymous-class declarations are included; blank anonymous-class names are preserved in the index. Java resource templates are included as source assets, even though their owning module does not compile them directly.

Length is the approximate physical span from declaration/annotations through closing brace; blank lines, comments and text-block contents count. Branch/depth screening counts `if`, loops, switches, `try`, `catch` and ternaries. It includes branches inside lambdas and treats else-if chains as nested syntax; it is not a calibrated cognitive-complexity score. Manual assessment used actual method bodies and surrounding ownership/contracts for the selected findings below. Remaining index rows are screening candidates, not assertions of complexity or instructions to refactor. This is static discovery; runtime correctness and full semantic clone equivalence were not tested.

The actionable inventory contains **77 bounded findings**, plus one exact-duplicate cleanup in the detailed duplication section. The [screening index](kiss-refactoring-method-index.csv) preserves every declaration over 20 lines, every declaration with screening depth at least three, and members of the repeated-body groups. It is sorted by Maven ownership, package and source location and records public/protected declaration visibility. The `kind` field is source-tree classification, not a claim that every build-support class is production code.

## Assignment boundaries and priorities

- **P1:** highest readability payoff in stateful, resource-owning or security-sensitive workflows. These priorities express refactoring value, not proven correctness/security defects.
- **P2:** coherent local extraction or repeated-policy simplification with clear benefits.
- **P3:** optional cleanup; proceed only if the resulting method is easier to explain. Keeping the current code is acceptable when extraction increases indirection.
- Each finding is a candidate for one small assignment. Work within the listed class/package; preserve method signatures, annotations, exception categories, package names and wire/persistence formats. Public/protected rows identify existing declarations touched by an implementation-body refactor, not approved API changes.
- Split large P1 work into separately tested local stages when necessary. Do not run concurrent assignments against the same class. Pair `TafBaseTest.closeAfterSetupFailure` and `finishInvocation` as one cleanup-focused assignment. Separate Apple request preparation, response handling and controller cleanup assignments; they do not require each other to complete.
- Acceptance for each future assignment: explain the top-level flow without reconstructing nested callbacks; preserve the row-specific behavior below; add/adjust meaningful characterization tests for changed production behavior; pass the narrow affected module tests and required cross-boundary/architecture gates. No new public helpers or package moves are authorized by this report.

## Inventory by Maven module and package

### Root Maven project: source-launched build support (outside Maven source roots)

#### Package `(default)`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-001 / P2 | [ImagePolicy.evaluate(String)](../../build-support/ci/ImagePolicy.java#L31) | 68 | Evidence parsing, identity/provenance/control checks and three indexed inventories accumulate failures in one method. | Extract cohesive evidence-header, artifact and license checks in the existing source-launched utility. | Implementation method; no public/SPI signature change or package move. | Fail-closed secret rejection, complete failure accumulation, finding evaluation and output contract. |
| KISS-002 / P2 | [QuickStartDocumentationTest.main(String[])](../../build-support/docs/QuickStartDocumentationTest.java#L24) | 104 | Many unrelated topic, starter, snippet, deployment, BOM and link rules run in one entry point. | Extract named check families inside this build-support test; retain aggregated failures. | Existing public/protected declaration or override involved; preserve signature. No package move. | All current checks, diagnostics, document set and final failure aggregation. |
| KISS-003 / P2 | [ReleaseLicensePolicy.inspectComponent(Path, String, List&lt;String&gt;, Map&lt;String, String&gt;)](../../build-support/release/ReleaseLicensePolicy.java#L122) | 59 | Component identity, declaration rejection, license preference and conditional approval/evidence formatting mix in one method. | Extract permitted-license selection and conditional evidence handling locally. | Implementation method; no public/SPI signature change or package move. | Ambiguous declarations fail before selection; preference order and component-specific approvals. |
| KISS-004 / P2 | [SyncDocVersion.run(boolean)](../../build-support/scripts/SyncDocVersion.java#L43) | 45 | Inspection collection, fatal-on-write decisions, error printing and file rewriting are controlled by one mode flag. | Extract diagnostics and approved rewrite phase locally. | Implementation method; no public/SPI signature change or package move. | Validate all files before writes, BOM/newline preservation, exit status and --check read-only behavior. |
| KISS-005 / P2 | [SyncDocVersion.inspect(Path, Path, String)](../../build-support/scripts/SyncDocVersion.java#L172) | 45 | Byte/BOM decoding, marker scanning, replacement text, problem collection and placeholder detection share one mutable cursor routine. | Extract marker processing or placeholder checking locally; retain a clear scanning loop. | Implementation method; no public/SPI signature change or package move. | Marker grammar, line diagnostics, replacement counts and byte/newline-preserving output. |
| KISS-006 / P3 | [ReleaseLicensePolicy.matchingDelimiter(String, int, char, char)](../../build-support/release/ReleaseLicensePolicy.java#L340) | 17 | A 17-line parser has coupled quote, escape and delimiter-depth state hidden in compact else-if updates. | Expand state transitions and use clear early-continue branches; keep the scanner local. | Implementation method; no public/SPI signature change or package move. | Escaped quotes/backslashes, nested delimiters, invalid start and unmatched input; no new parser dependency. |

### codinglair-taf-runtime/codinglair-taf-reporting-allure

#### Package `com.codinglair.taf.runtime.core.reporting.impl.allure`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-007 / P2 | [AllureCliSingleFileReportGenerator.generate(GenerationRequest)](../../codinglair-taf-runtime/codinglair-taf-reporting-allure/src/main/java/com/codinglair/taf/runtime/core/reporting/impl/allure/AllureCliSingleFileReportGenerator.java#L17) | 50 | Process setup, start failure, wait interruption, timeout and exit diagnostics form one sequential block. | Extract the wait/exit-check phase locally; retain process ownership in generate. | Existing public/protected declaration or override involved; preserve signature. No package move. | Timeout units, forced termination, interrupt flag and phase-specific exception diagnostics. |
| KISS-008 / P2 | [AllureReporter.reportEvent(ReportEvent)](../../codinglair-taf-runtime/codinglair-taf-reporting-allure/src/main/java/com/codinglair/taf/runtime/core/reporting/impl/allure/AllureReporter.java#L138) | 35 | Started/completed event cases mutate different registries and update both test and step lifecycle state. | Separate started-event and completed-event handling within the adapter. | Existing public/protected declaration or override involved; preserve signature. No package move. | Duplicate-event suppression, parent fallback, step counts and vendor boundary. |
| KISS-009 / P2 | [AllureSingleFilePublisher.publish()](../../codinglair-taf-runtime/codinglair-taf-reporting-allure/src/main/java/com/codinglair/taf/runtime/core/reporting/impl/allure/AllureSingleFilePublisher.java#L89) | 41 | Directory reservation, staged generation, validation, publication and three cleanup targets share one exception scope. | Extract staged generation/publication as a cohesive local operation; retain cleanup ownership in publish. | Implementation method; no public/SPI signature change or package move. | Create-only targets, validation before/after move, cleanup on failures and publication exception phases. |

### codinglair-taf-runtime/codinglair-taf-runner-cucumber

#### Package `com.codinglair.taf.runtime.cucumber`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-010 / P2 | [CucumberScenarioSession.start(Scenario)](../../codinglair-taf-runtime/codinglair-taf-runner-cucumber/src/main/java/com/codinglair/taf/runtime/cucumber/CucumberScenarioSession.java#L57) | 34 | Scenario binding and session creation share a catch block that unwinds partial sessions, reports, context and fields. | Extract failed-start cleanup in the same class. | Implementation method; no public/SPI signature change or package move. | Independent Cucumber lifecycle, primary/suppressed failures and all field/context clearing. |
| KISS-011 / P2 | [CucumberScenarioSession.close()](../../codinglair-taf-runtime/codinglair-taf-runner-cucumber/src/main/java/com/codinglair/taf/runtime/cucumber/CucumberScenarioSession.java#L152) | 30 | Session detachment, lifecycle close, report outcome and unbinding depend on several captured mutable fields. | Name report completion locally and make detached state explicit. | Existing public/protected declaration or override involved; preserve signature. No package move. | Passed/failed scenario outcomes, cleanup exceptions, idempotence and guaranteed unbinding. |
| KISS-012 / P3 | [CucumberBusinessReportPlugin.caseFinished(TestCaseFinished)](../../codinglair-taf-runtime/codinglair-taf-runner-cucumber/src/main/java/com/codinglair/taf/runtime/cucumber/CucumberBusinessReportPlugin.java#L117) | 43 | Outcome mapping, a large attempt descriptor, result assembly and listener notification obscure the event handler. | Extract outcome mapping and descriptor creation locally. | Implementation method; no public/SPI signature change or package move. | Scenario identity/hash, timestamps, history analysis, removal from states and listener order. |

### codinglair-taf-runtime/codinglair-taf-runner-testng

#### Package `com.codinglair.taf.runtime.testng`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-013 / P2 | [TafBaseTest.closeAfterSetupFailure(ITestResult, InvocationDescriptor, Throwable)](../../codinglair-taf-runtime/codinglair-taf-runner-testng/src/main/java/com/codinglair/taf/runtime/testng/TafBaseTest.java#L151) | 15 | Short method nests cleanup, result mutation and finalization, with two secondary-failure paths. | Name setup-failure finalization and suppression steps locally; keep mandatory finally semantics explicit. | Implementation method; no public/SPI signature change or package move. | Primary failure identity, suppressed failures and result status; pair with finishInvocation assignment. |
| KISS-014 / P2 | [TafBaseTest.finishInvocation(ITestResult, InvocationDescriptor, boolean)](../../codinglair-taf-runtime/codinglair-taf-runner-testng/src/main/java/com/codinglair/taf/runtime/testng/TafBaseTest.java#L167) | 20 | Nested finally blocks encode attempt recording, report finalization and several independent context unbindings. | Extract one cohesive context-unbinding operation and clarify finalization sequence; no generic lifecycle layer. | Implementation method; no public/SPI signature change or package move. | Every unbinding runs on each failure path; preserve precedence of record/report/unbinding failures. |
| KISS-015 / P2 | [TestNgStructuredResultWriter.writeJson(TestNgExecutionResult)](../../codinglair-taf-runtime/codinglair-taf-runner-testng/src/main/java/com/codinglair/taf/runtime/testng/TestNgStructuredResultWriter.java#L11) | 69 | Nested attempt/artifact loops and optional failure-analysis branches obscure the JSON document shape. | Extract one attempt encoder, artifact-array encoder and failure-analysis encoder locally. | Existing public/protected declaration or override involved; preserve signature. No package move. | Existing JSON snapshots, attempt/artifact order, null representation and redaction. |

### codinglair-taf-runtime/codinglair-taf-runtime-core

#### Package `com.codinglair.taf.runtime.core.condition`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-016 / P1 | [AwaitableAssertion.await(Supplier&lt;Object&gt;, String)](../../codinglair-taf-runtime/codinglair-taf-runtime-core/src/main/java/com/codinglair/taf/runtime/core/condition/AwaitableAssertion.java#L119) | 114 | Polling, timeout bookkeeping, success variants, interruption, exception collection and result formatting occupy one loop; terminal branches repeat mutable-field updates. | Extract cohesive terminal-result construction and poll evaluation within this class; retain a visible polling loop. | Existing public/protected declaration or override involved; preserve signature. No package move. | Preserve current supplier evaluation frequency, poll counts, observed-value order and interruption; do not silently repair behavior during extraction. |

#### Package `com.codinglair.taf.runtime.core.controller`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-017 / P2 | [ControllerRegistry.Entry.exposed(Class&lt;T&gt;, ReportingActionInterceptor)](../../codinglair-taf-runtime/codinglair-taf-runtime-core/src/main/java/com/codinglair/taf/runtime/core/controller/ControllerRegistry.java#L108) | 38 | Double-checked lazy proxy creation contains nested reflection invocation and throwable unwrapping lambdas. | Move proxy invocation into a named private operation; retain cache and synchronized creation. | Implementation method; no public/SPI signature change or package move. | Preserve interface exposure, target annotations, exactly-once initialization and original throwable identity. |
| KISS-018 / P2 | [ControllerRegistry.Entry.initialize(ControllerContext, List&lt;Entry&lt;?&gt;&gt;)](../../codinglair-taf-runtime/codinglair-taf-runtime-core/src/main/java/com/codinglair/taf/runtime/core/controller/ControllerRegistry.java#L147) | 28 | Locked lifecycle transitions surround nested partial-initialization cleanup and failure suppression. | Name failed-initialization cleanup locally; keep state checks and transitions visible under the same lock. | Implementation method; no public/SPI signature change or package move. | Concurrency, FAILED/CLOSED behavior, acquisition order and suppressed cleanup failures. |

#### Package `com.codinglair.taf.runtime.core.history`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-019 / P2 | [FileExecutionHistoryRepository.read(Path, int, Duration, String)](../../codinglair-taf-runtime/codinglair-taf-runtime-core/src/main/java/com/codinglair/taf/runtime/core/history/FileExecutionHistoryRepository.java#L122) | 46 | File discovery, aggregate byte budgets, decoding, quarantine, filtering and result ordering share mutable loop state. | Separate bounded candidate discovery and record acceptance; keep budget accounting and corruption status explicit. | Implementation method; no public/SPI signature change or package move. | Maximum bytes/records, age/signature filtering, quarantine, sort order and partial-corruption status. |

#### Package `com.codinglair.taf.runtime.core.reporting`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-020 / P2 | [ReportingActionInterceptor.invoke(Method, Object[], Callable&lt;Object&gt;)](../../codinglair-taf-runtime/codinglair-taf-runtime-core/src/main/java/com/codinglair/taf/runtime/core/reporting/ReportingActionInterceptor.java#L25) | 27 | Validation success/failure branches duplicate argument interpretation and mix reporting with invocation. | Extract argument interpretation and one validation-outcome reporting operation in this class. | Existing public/protected declaration or override involved; preserve signature. No package move. | Preserve throwable identity, reported expected/actual values and single-step reporting. |
| KISS-021 / P2 | [ReportingBeanPostProcessor.postProcessAfterInitialization(Object, String)](../../codinglair-taf-runtime/codinglair-taf-runtime-core/src/main/java/com/codinglair/taf/runtime/core/reporting/ReportingBeanPostProcessor.java#L24) | 33 | Bean eligibility, proxy setup, target selection and checked/unchecked invocation adaptation are nested in one advice lambda. | Extract the invocation adapter into a named private method; leave eligibility and proxy construction together. | Existing public/protected declaration or override involved; preserve signature. No package move. | Proxy target selection, return values, Error propagation and report de-duplication. |
| KISS-022 / P2 | [StructuredResultWriter.writeJson(String, String, String, List&lt;TestStep&gt;, List&lt;TestArtifact&gt;, FailureAnalysis)](../../codinglair-taf-runtime/codinglair-taf-runtime-core/src/main/java/com/codinglair/taf/runtime/core/reporting/StructuredResultWriter.java#L36) | 57 | Two hand-built array loops interleave element encoding, comma placement and overall document structure. | Extract step-array and artifact-array append operations using existing escaping helpers. | Existing public/protected declaration or override involved; preserve signature. No package move. | Exact JSON fields, escaping, nulls, redaction and existing serialized-output contracts. |
| KISS-023 / P3 | [StructuredResultWriter.writeJUnitXml(String, String, String, String, List&lt;TestStep&gt;, FailureAnalysis)](../../codinglair-taf-runtime/codinglair-taf-runtime-core/src/main/java/com/codinglair/taf/runtime/core/reporting/StructuredResultWriter.java#L109) | 46 | Failure metadata emission is embedded in the top-level XML document construction. | Extract the cohesive failure-properties block; retain the XML assembly sequence. | Existing public/protected declaration or override involved; preserve signature. No package move. | Existing property names, order, escaping and output shape; no schema redesign. |

#### Package `com.codinglair.taf.runtime.core.security`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-024 / P1 | [ArtifactDownloadTransport.download(URI, Map&lt;String, String&gt;)](../../codinglair-taf-runtime/codinglair-taf-runtime-core/src/main/java/com/codinglair/taf/runtime/core/security/ArtifactDownloadTransport.java#L70) | 54 | Redirect traversal combines destination authorization, credential forwarding, request execution, deadline accounting and bounded body reading. | Extract request preparation and redirect validation locally; keep redirect loop and stream ownership visible. | Existing public/protected declaration or override involved; preserve signature. No package move. | Authorization on every hop, TLS downgrade denial, origin checks, total deadline, byte bound and interruption. |

### codinglair-taf-runtime/taf-api-rest

#### Package `com.codinglair.taf.api.rest`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-025 / P3 | [DefaultRestController.await(RestRequest, Predicate&lt;RestResponse&gt;, Duration, Duration)](../../codinglair-taf-runtime/taf-api-rest/src/main/java/com/codinglair/taf/api/rest/DefaultRestController.java#L108) | 29 | Polling interleaves condition evaluation with a nested deadline-to-sleep calculation and interruption translation. | Name bounded sleep calculation and interrupt-preserving wait locally. | Existing public/protected declaration or override involved; preserve signature. No package move. | Current initial request, deadline semantics, timeout cause and reporter behavior; no shared polling framework. |
| KISS-026 / P3 | [DefaultRestController.capture(RestRequest, RestResponse)](../../codinglair-taf-runtime/taf-api-rest/src/main/java/com/codinglair/taf/api/rest/DefaultRestController.java#L151) | 42 | Request and response evidence formatting sit beside header normalization and two artifact publications. | Extract request/response evidence formatting locally; reuse current sanitizers. | Implementation method; no public/SPI signature change or package move. | Sensitive cookie/header/body redaction, artifact names, sequence order and collector-only evidence. |

### codinglair-taf-runtime/taf-api-soap

#### Package `com.codinglair.taf.api.soap`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-027 / P2 | [DefaultSoapController.exchange(SoapRequest)](../../codinglair-taf-runtime/taf-api-soap/src/main/java/com/codinglair/taf/api/soap/DefaultSoapController.java#L68) | 62 | Envelope validation/security, multipart selection, HTTP setup/send, response parsing and evidence capture share one method. | Extract secured request construction and response mapping locally. | Existing public/protected declaration or override involved; preserve signature. No package move. | SOAP 1.1 action behavior, namespace validation, byte limit, security sanitization and interruption. |
| KISS-028 / P2 | [DefaultSoapController.parseResponse(HttpResponse&lt;byte[]&gt;)](../../codinglair-taf-runtime/taf-api-soap/src/main/java/com/codinglair/taf/api/soap/DefaultSoapController.java#L199) | 32 | Multipart boundary splitting, header decoding and envelope-versus-attachment classification occur inside one loop. | Extract part classification/decoding locally; retain parser and ordering. | Implementation method; no public/SPI signature change or package move. | Byte encodings, missing-boundary/envelope failures, attachment IDs/media defaults and order. |

### codinglair-taf-runtime/taf-consumer-conformance

#### Package `com.codinglair.taf.conformance`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-029 / P2 | [ConsumerProjectValidator.validateSources(Path, BlueprintDescriptor, List&lt;ConformanceViolation&gt;)](../../codinglair-taf-runtime/taf-consumer-conformance/src/main/java/com/codinglair/taf/conformance/ConsumerProjectValidator.java#L282) | 67 | Bootstrap discovery, source rules, sensitive-value scanning, listener ownership and several traceability checks mix in one traversal. | Extract per-file source-policy checking and named traceability phases in the existing validator. | Implementation method; no public/SPI signature change or package move. | Violation rule IDs, correction text, file traversal order and current heuristic coverage. |
| KISS-030 / P2 | [ConsumerProjectValidator.validateFeatureTraceability(Path, List&lt;ConformanceViolation&gt;)](../../codinglair-taf-runtime/taf-consumer-conformance/src/main/java/com/codinglair/taf/conformance/ConsumerProjectValidator.java#L521) | 47 | Filesystem traversal, tag parsing, duplicate tracking and matching data-resource checks are nested in a lambda. | Extract per-feature checks with the existing shared ID set. | Implementation method; no public/SPI signature change or package move. | Duplicate detection across files, unresolved-resource rules and unreadable-source diagnostics. |

### codinglair-taf-runtime/taf-data-migration

#### Package `com.codinglair.taf.migration`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-031 / P2 | [FlywayDataMigrationManager.execute(MigrationRequest)](../../codinglair-taf-runtime/taf-data-migration/src/main/java/com/codinglair/taf/migration/FlywayDataMigrationManager.java#L40) | 45 | Authorization/technology/location checks, target lookup, Flyway configuration and validate/migrate result mapping are interleaved. | Extract Flyway construction and outcome mapping locally; retain policy branching. | Existing public/protected declaration or override involved; preserve signature. No package move. | DISABLED behavior, cleanDisabled, baseline policy and migrate's own validation; do not add pre-migrate validation. |

### codinglair-taf-runtime/taf-database

#### Package `com.codinglair.taf.database`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-032 / P2 | [DefaultDatabaseController.transaction(Function&lt;JdbcTransaction, T&gt;)](../../codinglair-taf-runtime/taf-database/src/main/java/com/codinglair/taf/database/DefaultDatabaseController.java#L108) | 25 | Transaction work, commit, evidence, rollback and throwable translation nest three failure scopes. | Extract rollback-with-suppression locally; retain the transactional connection scope. | Existing public/protected declaration or override involved; preserve signature. No package move. | Commit/evidence ordering, rollback failure suppression, Error/runtime wrapping and connection close behavior. |
| KISS-033 / P2 | [DefaultDatabaseController.close()](../../codinglair-taf-runtime/taf-database/src/main/java/com/codinglair/taf/database/DefaultDatabaseController.java#L146) | 23 | Authorized cleanup SQL and tracked connection closure have different error handling but share one failure accumulator. | Extract tracked-connection closure locally; keep explicit aggregate failure handling. | Existing public/protected declaration or override involved; preserve signature. No package move. | Idempotence, SQL authorization, all connections attempted, suppression order and clearing. |

### codinglair-taf-runtime/taf-environments

#### Package `com.codinglair.taf.runtime.environment`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-034 / P1 | [ContainerLifecycleCoordinator.acquire(EnvironmentRequest, EnvironmentType, Supplier&lt;? extends ManagedContainer&gt;)](../../codinglair-taf-runtime/taf-environments/src/main/java/com/codinglair/taf/runtime/environment/ContainerLifecycleCoordinator.java#L15) | 55 | Isolated/shared paths combine startup, key selection, reference increments, lease creation and two rollback policies. | Extract isolated acquisition and shared lease creation in the same class. | Implementation method; no public/SPI signature change or package move. | Keep shared operations under monitor; reference counts, startup rollback, identity and cleanup suppression. |

#### Package `com.codinglair.taf.runtime.environment.spring`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-035 / P2 | [EnvironmentConsumerPreflightContributor.inspect()](../../codinglair-taf-runtime/taf-environments/src/main/java/com/codinglair/taf/runtime/environment/spring/EnvironmentConsumerPreflightContributor.java#L27) | 81 | Nested capability lambdas check four rule families before a second contributor/capability traversal with exception translation. | Extract per-capability validation and contributor invocation locally. | Existing public/protected declaration or override involved; preserve signature. No package move. | Enabled-only checks, contributor matching, diagnostic IDs/order and sanitized failure messages. |

### codinglair-taf-runtime/taf-file

#### Package `com.codinglair.taf.runtime.file`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-036 / P3 | [StructuredFileComparator.fixed(CheckedFile, FileComparisonOptions)](../../codinglair-taf-runtime/taf-file/src/main/java/com/codinglair/taf/runtime/file/StructuredFileComparator.java#L176) | 22 | Reader lifetime, row iteration, width offsets, per-column extraction and two row-length failure cases are nested. | Extract fixed-width row mapping locally with explicit row number. | Implementation method; no public/SPI signature change or package move. | Charset, offsets, key format and short/long-row failures; retain bounded file access. |
| KISS-037 / P3 | [StructuredFileComparator.excel(CheckedFile)](../../codinglair-taf-runtime/taf-file/src/main/java/com/codinglair/taf/runtime/file/StructuredFileComparator.java#L199) | 26 | ZIP selection/byte accounting, shared-string decoding and worksheet traversal share one method. | Extract bounded workbook-entry reading locally; retain existing worksheet decoder. | Implementation method; no public/SPI signature change or package move. | Expanded-byte bound, required worksheet detection and existing comparison keys. |

### codinglair-taf-runtime/taf-messaging-aws

#### Package `com.codinglair.taf.messaging.aws.common`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-038 / P2 | [DefaultAwsControllers.DefaultSqsController.receive(SqsReceiveRequest)](../../codinglair-taf-runtime/taf-messaging-aws/src/main/java/com/codinglair/taf/messaging/aws/common/DefaultAwsControllers.java#L180) | 46 | Timeout/admin validation, SDK request setup, native mapping and in-flight-versus-restored message handling share one method. | Extract SDK request construction and received-message classification locally. | Existing public/protected declaration or override involved; preserve signature. No package move. | Administrative bounds, replacement of old in-flight receipts, visibility restoration and sanitized failures. |
| KISS-039 / P2 | [DefaultAwsControllers.DefaultSqsController.close()](../../codinglair-taf-runtime/taf-messaging-aws/src/main/java/com/codinglair/taf/messaging/aws/common/DefaultAwsControllers.java#L371) | 25 | Short cleanup loop combines visibility restoration, expired-receipt classification, removal and aggregate failure handling. | Extract per-receipt visibility restoration locally; retain aggregate and client ownership. | Existing public/protected declaration or override involved; preserve signature. No package move. | No destructive queue cleanup, idempotence, all receipts attempted and suppression semantics. |
| KISS-040 / P2 | [DefaultAwsControllers.DefaultEventBridgeController.publish(List&lt;EventPublishRequest&gt;)](../../codinglair-taf-runtime/taf-messaging-aws/src/main/java/com/codinglair/taf/messaging/aws/common/DefaultAwsControllers.java#L532) | 50 | Input indexing ties request-entry creation, partial response fallback, sanitized evidence and publication together. | Extract request-entry and per-index result/evidence construction in this class. | Existing public/protected declaration or override involved; preserve signature. No package move. | Input/result index alignment, MissingResult fallback, ten-entry bound and evidence sanitization. |

#### Package `com.codinglair.taf.messaging.aws.environment`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-041 / P2 | [LocalStackEnvironmentProvider.provisionEventBuses(AwsConnectionProperties, String, Map&lt;String, String&gt;, Clients, List&lt;AwsOwnershipManifestEntry&gt;)](../../codinglair-taf-runtime/taf-messaging-aws/src/main/java/com/codinglair/taf/messaging/aws/environment/LocalStackEnvironmentProvider.java#L199) | 61 | Each bus branch provisions a rule, SQS policy and target while registering ownership entries between side effects. | Extract routed-target provisioning locally; keep ownership entries immediately after successful effects. | Implementation method; no public/SPI signature change or package move. | Physical names, ownership/cleanup ordering, external resources untouched and partial-failure evidence. |

### codinglair-taf-runtime/taf-messaging-jms

#### Package `com.codinglair.taf.messaging.jms`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-042 / P2 | [DefaultJmsController.receive(JmsDestination, String, String, MessageQuery)](../../codinglair-taf-runtime/taf-messaging-jms/src/main/java/com/codinglair/taf/messaging/jms/DefaultJmsController.java#L150) | 52 | Destination locking, buffered matches, JMS resource creation, timed polling, matching and evidence/buffering are deeply nested. | Extract the polling phase and bounded unmatched buffering locally. | Implementation method; no public/SPI signature change or package move. | Destination lock scope, durable subscriptions, auto-ack behavior, selector, timeout and interruption. |

### codinglair-taf-runtime/taf-messaging-kafka

#### Package `com.codinglair.taf.messaging.kafka`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-043 / P2 | [DefaultKafkaController.consume(MessageQuery, String)](../../codinglair-taf-runtime/taf-messaging-kafka/src/main/java/com/codinglair/taf/messaging/kafka/DefaultKafkaController.java#L156) | 48 | Topic locking, cached results, consumer ownership, polling, native conversion, commit ordering and buffer updates mix in one block. | Extract records handling locally while retaining locked polling and consumer lifetime. | Existing public/protected declaration or override involved; preserve signature. No package move. | Commit-after-match and unmatched commit order, group, buffer bound and interrupt conversion. |
| KISS-044 / P2 | [DefaultKafkaController.ensureTopic(String)](../../codinglair-taf-runtime/taf-messaging-kafka/src/main/java/com/codinglair/taf/messaging/kafka/DefaultKafkaController.java#L248) | 41 | Describe/create phases repeat interruption/timeout handling and embed policy decisions in exception branches. | Separate topic lookup from conditional creation locally; use direct helpers, no generic future executor. | Implementation method; no public/SPI signature change or package move. | REQUIRE_EXISTING semantics and tolerated TopicExistsException race; preserve failure categories. |

### codinglair-taf-runtime/taf-messaging-rabbitmq

#### Package `com.codinglair.taf.messaging.rabbitmq`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-045 / P2 | [DefaultRabbitController.consume(MessageQuery, RabbitAcknowledgment)](../../codinglair-taf-runtime/taf-messaging-rabbitmq/src/main/java/com/codinglair/taf/messaging/rabbitmq/DefaultRabbitController.java#L135) | 48 | Queue locking and polling combine sleep budgeting, conversion, match-specific disposal and unmatched acknowledgment/buffering. | Extract matched-delivery completion locally and name bounded wait calculation. | Existing public/protected declaration or override involved; preserve signature. No package move. | ACK/NACK policy, unmatched acknowledgment order, lock/resource ownership and cancellation. |

### codinglair-taf-runtime/taf-mobile-appium

#### Package `com.codinglair.taf.mobile.appium`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-046 / P1 | [AppleProtocolFixture.start()](../../codinglair-taf-runtime/taf-mobile-appium/src/test/java/com/codinglair/taf/mobile/appium/AppleProtocolFixture.java#L31) | 99 | Test server setup embeds a large route/failure dispatcher; else-if chain, context mutation and response encoding are difficult to scan. | Extract request handling, evidence responses and response writing inside this test fixture; use direct route dispatch. | Implementation method; no public/SPI signature change or package move. | Keep path precedence, unsupported/failure canaries, request recording and context readiness state. |
| KISS-047 / P2 | [AppleTransportSecurityTest.secureFixture()](../../codinglair-taf-runtime/taf-mobile-appium/src/test/java/com/codinglair/taf/mobile/appium/AppleTransportSecurityTest.java#L64) | 52 | Test server setup mixes header/request recording, redirect simulation, error/security payload selection and writing. | Extract per-request handler and secure response selection as test-local helpers. | Implementation method; no public/SPI signature change or package move. | Redirect path, header recording, canary coverage and session/source/screenshot responses. |

#### Package `com.codinglair.taf.mobile.appium.configuration`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-048 / P2 | [AppleAuthentication.validate()](../../codinglair-taf-runtime/taf-mobile-appium/src/main/java/com/codinglair/taf/mobile/appium/configuration/AppleAuthentication.java#L60) | 55 | Mechanism checks mix with provider JSON-path expansion, capability validation and overlapping-path detection inside a lambda. | Separate mechanism-specific checks and provider-path validation locally. | Implementation method; no public/SPI signature change or package move. | Reserved headers, opaque references, overlap rejection and sanitized errors; do not resolve secrets. |
| KISS-049 / P2 | [AppleControllerSettings.validate()](../../codinglair-taf-runtime/taf-mobile-appium/src/main/java/com/codinglair/taf/mobile/appium/configuration/AppleControllerSettings.java#L188) | 69 | Common target/timeouts and mutually exclusive Safari/preinstalled/packaged rules form a long nested decision tree. | Extract common and execution/application-mode validation locally. | Existing public/protected declaration or override involved; preserve signature. No package move. | Same first failure/messages, allowed combinations, URI/path safety and provider-map ownership. |
| KISS-050 / P3 | [AppleControllerSettings.merge(AppleControllerSettings, AppleControllerSettings, AppleControllerSettings)](../../codinglair-taf-runtime/taf-mobile-appium/src/main/java/com/codinglair/taf/mobile/appium/configuration/AppleControllerSettings.java#L113) | 60 | Layer precedence is obscured by app-reference subfield merging among dozens of direct assignments and map merges. | Extract app-reference merging and one-layer application locally; keep explicit field assignments. | Existing public/protected declaration or override involved; preserve signature. No package move. | Null inherits, false overrides, layer order and recursive-map collision behavior; no reflection-based merge. |

#### Package `com.codinglair.taf.mobile.appium.evidence`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-051 / P1 | [AppleEvidence.collect(IOSDriver, ArtifactReason)](../../codinglair-taf-runtime/taf-mobile-appium/src/main/java/com/codinglair/taf/mobile/appium/evidence/AppleEvidence.java#L48) | 84 | Capture budgets, metadata, failure/explicit policy, screenshots/source/logs and stateful video stop are assembled in one method. | Extract metadata/log capture and visual/video blocks locally; keep capture budget and state transition ownership visible. | Existing public/protected declaration or override involved; preserve signature. No package move. | Four-batch budget, video stop once, required-evidence behavior, byte bounds and collector/redaction path. |

#### Package `com.codinglair.taf.mobile.appium.platform`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-052 / P1 | [AppleHttpClient.execute(org.openqa.selenium.remote.http.HttpRequest)](../../codinglair-taf-runtime/taf-mobile-appium/src/main/java/com/codinglair/taf/mobile/appium/platform/AppleHttpClient.java#L61) | 175 | Transport method combines path policy, resource inspection, three authentication modes, secret injection, network exchange, signed artifact retrieval and response scrubbing. | Split into local request preparation, authenticated capability injection and safe response handling; keep trusted execution/secret lifetime in execute. | Existing public/protected declaration or override involved; preserve signature. No package move. | No secret-bearing public intermediate, no raw exception cause, per-command authorization, byte/deadline bounds and connectionAttempted semantics. |
| KISS-053 / P2 | [AppleHttpClient.scrub(Object, List&lt;String&gt;)](../../codinglair-taf-runtime/taf-mobile-appium/src/main/java/com/codinglair/taf/mobile/appium/platform/AppleHttpClient.java#L319) | 42 | Recursive type switch combines credential replacement, secret-key filtering and nested JSON string decoding. | Name string scrubbing and map filtering locally; retain one clear recursive traversal. | Implementation method; no public/SPI signature change or package move. | Nested response redaction, secret-value matching and existing exclusions; no logging of raw values. |

#### Package `com.codinglair.taf.mobile.appium.service`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-054 / P1 | [DefaultAppleController.initialize(ControllerContext)](../../codinglair-taf-runtime/taf-mobile-appium/src/main/java/com/codinglair/taf/mobile/appium/service/DefaultAppleController.java#L112) | 58 | Initialization phases share state with remote-uncertainty detection, partial-driver cleanup, owned allocation reconciliation and reservation quarantine. | Extract failed-initialization cleanup and owned-session reconciliation locally. | Existing public/protected declaration or override involved; preserve signature. No package move. | Preserve READY/FAILED transitions, interrupt flag, ownership checks, uncertainty quarantine and sanitized suppressed failures. |
| KISS-055 / P1 | [DefaultAppleController.close()](../../codinglair-taf-runtime/taf-mobile-appium/src/main/java/com/codinglair/taf/mobile/appium/service/DefaultAppleController.java#L250) | 46 | Evidence finalization, app termination/uninstall, quit uncertainty and reservation release are linked through mutable failure flags. | Extract owned-driver teardown locally with explicit returned outcome using existing values; keep state detachment visible. | Existing public/protected declaration or override involved; preserve signature. No package move. | No cleanup of unowned apps/sessions, Safari restrictions, installedByController, quarantine and idempotence. |

### codinglair-taf-runtime/taf-secrets-local

#### Package `com.codinglair.taf.runtime.secret.spring`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-056 / P2 | [SecretAutoConfiguration.selectProviders(Map&lt;String, SecretProvider&gt;, SecretProperties, Environment)](../../codinglair-taf-runtime/taf-secrets-local/src/main/java/com/codinglair/taf/runtime/secret/spring/SecretAutoConfiguration.java#L71) | 47 | Selection defaults/ambiguity rules are followed by routing validation inside a mutable-map lambda. | Extract explicit routing resolution locally; retain default/ambiguity checks in selectProviders. | Implementation method; no public/SPI signature change or package move. | taf-local fallback, provider ID/bean matching, duplicate routes and diagnostic keys. |
| KISS-057 / P2 | [SecretPreflightContributor.inspect()](../../codinglair-taf-runtime/taf-secrets-local/src/main/java/com/codinglair/taf/runtime/secret/spring/SecretPreflightContributor.java#L28) | 47 | Reference parsing/deduplication and provider readiness use separate traversals and different diagnostic paths in one method. | Extract reference collection and provider readiness checks locally. | Existing public/protected declaration or override involved; preserve signature. No package move. | Deduplicate by provider, placeholder/malformed handling and sanitized bootstrap diagnostics. |

### codinglair-taf-runtime/taf-test-definitions

#### Package `com.codinglair.taf.runtime.definition`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-058 / P1 | [CsvSecretNormalizer.normalize(CsvSecretNormalizationRequest, SecretProvisioner)](../../codinglair-taf-runtime/taf-test-definitions/src/main/java/com/codinglair/taf/runtime/definition/CsvSecretNormalizer.java#L25) | 71 | Authorization, CSV parsing, per-field classification, transient plaintext protection, serialization, concurrent-change checks and atomic replacement share one method. | Extract per-row classified-field normalization and canonical commit phase locally. | Existing public/protected declaration or override involved; preserve signature. No package move. | No plaintext persistence/reporting, transient value closure, row/field order, digest check, line endings and provisioner readiness. |
| KISS-059 / P2 | [CsvTestDefinitionRepository.read(DefinitionResourceLocation, String, String, Map&lt;String, SecretFieldDefinition&gt;, ClassLoader)](../../codinglair-taf-runtime/taf-test-definitions/src/main/java/com/codinglair/taf/runtime/definition/CsvTestDefinitionRepository.java#L89) | 48 | CSV resource opening, row identity validation, duplicate tracking and secret checks mix inside one traversal. | Extract row validation/materialization locally after inspecting neighboring require and validateSecrets. | Implementation method; no public/SPI signature change or package move. | Duplicate/blank IDs, classified references, profile/name lookup and existing diagnostics. |
| KISS-060 / P2 | [FileTestDefinitionRepository.writeDocument(Document)](../../codinglair-taf-runtime/taf-test-definitions/src/main/java/com/codinglair/taf/runtime/definition/FileTestDefinitionRepository.java#L202) | 29 | Nested write/move/fallback/finally scopes obscure temporary-file ownership and atomic replacement policy. | Name temporary serialization and move locally; keep unconditional temp cleanup explicit. | Implementation method; no public/SPI signature change or package move. | ATOMIC_MOVE fallback, canonical serialization and current cleanup exception precedence. |

### codinglair-taf-runtime/taf-test-definitions-mongodb

#### Package `com.codinglair.taf.runtime.definition.mongodb`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-061 / P1 | [GridFsPayloadResolver.stage(PayloadReference, PayloadRange)](../../codinglair-taf-runtime/taf-test-definitions-mongodb/src/main/java/com/codinglair/taf/runtime/definition/mongodb/GridFsPayloadResolver.java#L42) | 64 | Metadata validation, temporary staging, delegate resolver setup and returned cleanup closure share two repeated catch cleanup blocks. | Extract staged-file deletion fallback and delegate opening locally; keep returned resource lifetime explicit. | Implementation method; no public/SPI signature change or package move. | Size/media/checksum/range validation, delegate close, staged deletion and deleteOnExit fallback. |

### codinglair-taf-runtime/taf-web-playwright

#### Package `com.codinglair.taf.web.playwright`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-062 / P2 | [DefaultPlaywrightController.initialize(ControllerContext)](../../codinglair-taf-runtime/taf-web-playwright/src/main/java/com/codinglair/taf/web/playwright/DefaultPlaywrightController.java#L49) | 50 | Local/remote browser setup, context options, trace/video policy and evidence listeners share partial-initialization handling. | Extract browser creation and context options locally. | Existing public/protected declaration or override involved; preserve signature. No package move. | Lazy startup, visual policy before capture, remote endpoint behavior and partial cleanup. |
| KISS-063 / P2 | [DefaultPlaywrightController.collectArtifacts(ArtifactReason)](../../codinglair-taf-runtime/taf-web-playwright/src/main/java/com/codinglair/taf/web/playwright/DefaultPlaywrightController.java#L143) | 37 | Read-only capture is mixed with trace stop and browser-context closure needed to finalize video, mutating page/context fields. | Extract video finalization and failure captures locally; make resource mutation visible. | Existing public/protected declaration or override involved; preserve signature. No package move. | Capture ordering, trace/video timing, collector publication and cleanup failure behavior. |

### demos/playwright-sauce-demo

#### Package `com.codinglair.taf.demo.sauce.quickstart`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-064 / P2 | [FrontToBackExample.tracesOneOwnedOrderAcrossPublicLayers()](../../demos/playwright-sauce-demo/src/test/java/com/codinglair/taf/demo/sauce/quickstart/FrontToBackExample.java#L33) | 73 | Demonstration combines order creation, UI action, API/database polling and failure-preserving DELETE cleanup. | Extract order completion check and owned-order cleanup locally; leave the end-to-end narrative visible. | Existing public/protected declaration or override involved; preserve signature. No package move. | Use only existing Runtime APIs, correlation ID, polling criteria and primary/suppressed failures. |

### taf-mcp-server/taf-execution-worker

#### Package `com.codinglair.taf.mcp.worker`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-065 / P1 | [LocalExecutionWorker.execute(WorkerRequest, CancellationToken, ResourceAuthorizer)](../../taf-mcp-server/taf-execution-worker/src/main/java/com/codinglair/taf/mcp/worker/LocalExecutionWorker.java#L76) | 91 | Workflow authorization, workspace/process ownership, virtual-thread output draining, cancellation/deadline polling and artifact/result collection occupy one failure scope. | Extract request checks and process-wait phase locally; retain process/executor/workspace cleanup visibly. | Existing public/protected declaration or override involved; preserve signature. No package move. | Allowlist/resources, total limits, tree termination, drain deadline, interruption, output redaction and current cleanup failure precedence. |

### taf-mcp-server/taf-mcp-jobs

#### Package `com.codinglair.taf.mcp.jobs`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-066 / P2 | [LocalJobRepository.write(Path, Job)](../../taf-mcp-server/taf-mcp-jobs/src/main/java/com/codinglair/taf/mcp/jobs/LocalJobRepository.java#L214) | 44 | Binary job-field encoding, two collection loops, temp-file publication and finally cleanup are interleaved. | Extract binary encoding locally; keep temp lifecycle and move in write. | Implementation method; no public/SPI signature change or package move. | MAGIC/version, exact field order, UTF encoding and secondary cleanup failure behavior. |

### taf-mcp-server/taf-mcp-security

#### Package `com.codinglair.taf.mcp.security`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-067 / P2 | [McpEnforcementService.enforce(EnforcementRequest, Callable&lt;?&gt;)](../../taf-mcp-server/taf-mcp-security/src/main/java/com/codinglair/taf/mcp/security/McpEnforcementService.java#L35) | 71 | Input admission, policy decision, approval, side effect, redaction and five audit outcomes form a long security sequence. | Extract input rejection and audited execution completion/failure locally; keep authorization/approval order visible. | Existing public/protected declaration or override involved; preserve signature. No package move. | No side effect before checks, operation digest binding, audit event data/order, redaction and interruption. |

### taf-mcp-server/taf-mcp-tools

#### Package `com.codinglair.taf.mcp.tools`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-068 / P1 | [BlueprintCompositionEngine.normalize(Request, String, List&lt;Diagnostic&gt;)](../../taf-mcp-server/taf-mcp-tools/src/main/java/com/codinglair/taf/mcp/tools/BlueprintCompositionEngine.java#L120) | 131 | General coordinate validation, capability/messaging defaults and deeply nested Android/Apple/family/mode rules share many mutable tokens. | Extract mobile-selection normalization in this class and keep general request assembly visible. | Implementation method; no public/SPI signature change or package move. | Diagnostic accumulation/order, Android defaults, Apple canonicalization, Safari exclusions and existing constructors. |
| KISS-069 / P1 | [BlueprintCompositionEngine.render(List&lt;Contribution&gt;, NormalizedRequest, List&lt;Dependency&gt;, Path, List&lt;Diagnostic&gt;)](../../taf-mcp-server/taf-mcp-tools/src/main/java/com/codinglair/taf/mcp/tools/BlueprintCompositionEngine.java#L355) | 80 | Nested asset traversal performs token substitution, four rejection checks, path ownership and planned-write hashing. | Extract render-one-asset and its validation locally; keep deterministic ownership maps in render. | Implementation method; no public/SPI signature change or package move. | Case-fold collisions, confinement, no writes during planning, unresolved tokens and diagnostics/order. |
| KISS-070 / P2 | [BlueprintCompositionEngine.select(NormalizedRequest, List&lt;Contribution&gt;, List&lt;Diagnostic&gt;)](../../taf-mcp-server/taf-mcp-tools/src/main/java/com/codinglair/taf/mcp/tools/BlueprintCompositionEngine.java#L252) | 51 | A stream both selects catalog entries and rewrites Apple mobile assets, before cardinality validation. | Extract selected-contribution adaptation locally; keep sorting and cardinality checks visible. | Implementation method; no public/SPI signature change or package move. | Manifest IDs, common/capability count rules and deterministic order. |
| KISS-071 / P2 | [BlueprintCompositionEngine.validateConfiguration(List&lt;Contribution&gt;, List&lt;Diagnostic&gt;)](../../taf-mcp-server/taf-mcp-tools/src/main/java/com/codinglair/taf/mcp/tools/BlueprintCompositionEngine.java#L475) | 26 | Triple loop embeds overlap, merge-rule compatibility and value-equality policy in one compound condition. | Name the incompatible-claim predicate locally; keep pairwise traversal explicit. | Implementation method; no public/SPI signature change or package move. | REQUIRE_EQUAL exception, pointer/document overlap and diagnostic ordering. |
| KISS-072 / P2 | [McpWorkflowTools.invoke(ToolRequest)](../../taf-mcp-server/taf-mcp-tools/src/main/java/com/codinglair/taf/mcp/tools/McpWorkflowTools.java#L92) | 49 | Authorization request assembly and enforced callback are followed by two switches over the same status for outcome/message mapping. | Extract denied-result mapping in one local switch and name the enforced workflow callback. | Existing public/protected declaration or override involved; preserve signature. No package move. | Status/outcome/message mapping, capability preflight within enforcement and response redaction. |
| KISS-073 / P2 | [McpWorkflowTools.run(JobId, ToolRequest)](../../taf-mcp-server/taf-mcp-tools/src/main/java/com/codinglair/taf/mcp/tools/McpWorkflowTools.java#L188) | 41 | Optimistic job updates, workflow execution, terminal outcome mapping and cancellation conflict recovery share one method. | Extract terminal transition selection locally; keep persistence versions and conflict recovery visible. | Implementation method; no public/SPI signature change or package move. | Cancellation races, expected versions, progress checkpoints and failRunningJob behavior. |
| KISS-074 / P2 | [ScaffoldWorkflow.applyApproved(Path, ScaffoldTemplate)](../../taf-mcp-server/taf-mcp-tools/src/main/java/com/codinglair/taf/mcp/tools/ScaffoldWorkflow.java#L162) | 64 | Create-only writes, per-file provenance, diff text, compilation and rollback share one method with parallel indexed lists. | Extract provenance and diff construction locally; leave write/rollback ownership explicit. | Implementation method; no public/SPI signature change or package move. | Create-only semantics, target/asset alignment, rollback on I/O conflict and compilation-failed result behavior. |
| KISS-075 / P2 | [AppleBlueprintTest.fixtures()](../../taf-mcp-server/taf-mcp-tools/src/test/java/com/codinglair/taf/mcp/tools/AppleBlueprintTest.java#L220) | 53 | Nested platform/mode generation uses chained ternaries for topology and then repeats plan/write assertions for packaged variants. | Name fixture request creation and plan/write assertion locally. | Implementation method; no public/SPI signature change or package move. | Full platform/mode coverage, opt-in property, destination names and packaged variants. |

### taf-mcp-server/taf-mcp-transport-http

#### Package `com.codinglair.taf.mcp.http`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-076 / P3 | [HttpAdmissionFilter.doFilterInternal(HttpServletRequest, HttpServletResponse, FilterChain)](../../taf-mcp-server/taf-mcp-transport-http/src/main/java/com/codinglair/taf/mcp/http/HttpAdmissionFilter.java#L35) | 35 | Admission semaphore, rate checks, session admission and deadline header sit together with post-chain session removal. | Extract session-admission check locally only if it makes the request flow clearer. | Existing public/protected declaration or override involved; preserve signature. No package move. | Release semaphore on all paths, status codes, DELETE cleanup timing and clock-derived deadline. |

### taf-mcp-server/taf-mcp-transport-stdio

#### Package `com.codinglair.taf.mcp.stdio`

| ID / priority | Class and method (source) | Approx. lines | Readability problem | Recommended bounded scope | API/SPI / location | Behavior and verification focus |
| --- | --- | ---: | --- | --- | --- | --- |
| KISS-077 / P3 | [StdioProtocolInput.nextValidFrame()](../../taf-mcp-server/taf-mcp-transport-stdio/src/main/java/com/codinglair/taf/mcp/stdio/StdioProtocolInput.java#L55) | 25 | Outer rejection loop and inner byte loop mix framing, oversize tracking, EOF and JSON acceptance. | Extract bounded frame reading locally using existing private representation or simple fields; avoid a framing framework. | Implementation method; no public/SPI signature change or package move. | Drain oversized line, EOF behavior, newline normalization and stderr-only rejection diagnostics. |

## Exact duplication: bounded cleanup and deliberate deferrals

Repeated bodies were screened by whitespace-normalized whole-method text (minimum eight physical lines and 200 source characters), then significant production pairs were inspected. Normalizing whitespace is a candidate detector, not a token-aware proof: it can also collapse whitespace inside literals. The selected hashing/buffering/default-history pairs below were directly inspected. This does not detect renamed variables, all partial blocks, or every tiny clone. Cross-module consolidation must not create new dependencies or make Runtime depend on MCP.

### Runtime core: `com.codinglair.taf.runtime.core.reporting` and `.reporting.abstraction`

| Finding | Methods / length | Reason | Scope | API / package involvement | Verification |
| --- | --- | --- | --- | --- | --- |
| DUP-001 / P2 | [ArtifactCollector.computeHash(String)](../../codinglair-taf-runtime/codinglair-taf-runtime-core/src/main/java/com/codinglair/taf/runtime/core/reporting/ArtifactCollector.java#L125) (12); [TestArtifact.computeHash(String)](../../codinglair-taf-runtime/codinglair-taf-runtime-core/src/main/java/com/codinglair/taf/runtime/core/reporting/abstraction/TestArtifact.java#L57) (12) | Identical null handling, SHA-256/Base64 calculation and exception translation can drift. | Keep both public entry points; delegate the collector method to the already existing `TestArtifact.computeHash`. No new helper type or layer. | Both methods are public; signatures and both package locations remain. | Preserve current default-charset bytes, null behavior, Base64 and exception contract; add focused equivalence coverage. Charset changes are a separate behavior assignment. |

### Copies to record without forcing a new shared abstraction

| Owning modules / packages | Methods / lines | Assessment and independent scope | API / package involvement |
| --- | --- | --- | --- |
| `codinglair-taf-runner-testng`, `com.codinglair.taf.runtime.testng` | `TafBaseTest.hash(String)` (10, line 319); `TestNgLifecycleListener.safeId(String)` (10, line 330) | Exact hash-body duplicate. Defer consolidation: separate owners and no inspected reusable helper justify a new utility for ten straightforward lines. If an existing approved helper is identified later, use it without changing encoding. | Implementation helpers; no signature/package changes proposed. |
| `codinglair-taf-runner-cucumber` / `codinglair-taf-runner-testng`, `com.codinglair.taf.runtime.cucumber` / `.testng` | `CucumberBusinessReportPlugin.hash(String)` (10, line 186), the two TestNG hash methods above; `CucumberBusinessReportPlugin.disabledHistory()` (17, line 197) / `TestNgLifecycleListener.disabledHistory()` (17, line 341) | Identical hashing and disabled-history wiring. Defer cross-runner consolidation: runners remain independent, and a new core factory solely to eliminate this copy is outside KISS scope. Keep constants/semantics aligned in future owner-local work. | Private implementation; no new cross-runner dependency or core API proposed. |
| `taf-messaging-kafka` / `taf-messaging-rabbitmq`, `com.codinglair.taf.messaging.kafka` / `.rabbitmq` | `DefaultKafkaController.removeMatch(MessageQuery)` (13, line 359) / `DefaultRabbitController.removeMatch(MessageQuery)` (13, line 297) | Exact iterator-removal copy. Readable and intentional provider-local state handling. Retain; do not add a common buffer abstraction just for this loop. | Private methods; no package/signature change. |
| `codinglair-taf-runtime-core` / `taf-mcp-tools`, `com.codinglair.taf.runtime.core.history` / `com.codinglair.taf.mcp.tools` | `FileExecutionHistoryRepository.hash(String)` (9, line 309) / `ScaffoldWorkflow.digest(String)` (9, line 286) | Exact SHA-256 copy. Retain rather than introduce a new shared layer or reverse dependency. | Private methods; no public API change. |
| `taf-file` / `taf-test-definitions-mongodb`, `com.codinglair.taf.runtime.file` / `.definition.mongodb` | `FilePayloadResolver.failure(...)` (10, line 101) / `GridFsPayloadResolver.failure(...)` (10, line 117) | Exact payload diagnostic construction. Retain at provider boundaries; a new public failure factory is not warranted. | Private methods; no package/API change. |
| `taf-mcp-transport-http` / `taf-mcp-transport-stdio`, `com.codinglair.taf.mcp.http` / `.stdio` | `HttpWorkflowTools.requiredCapability(Object)` (13, line 104) / `StdioWorkflowTools.requiredCapability(Object)` (13, line 111) | Exact input-shape capability mapping. Record drift risk; defer until an existing application-layer owner can be reused without widening its API. No new transport-common abstraction proposed. | Implementation helpers; preserve transport boundaries. |
| `taf-api-soap`, `taf-messaging-jms`, `taf-messaging-kafka`, `taf-messaging-rabbitmq`; respective adapter packages | `SoapAutoConfiguration.soapPreflightContributor` (9, line 48), `JmsAutoConfiguration.jmsPreflightContributor` (9, line 46), `KafkaAutoConfiguration.kafkaPreflightContributor` (9, line 49), `RabbitAutoConfiguration.rabbitPreflightContributor` (9, line 53) | Same short anonymous-contributor shape with module-specific properties. Keep local; a generic registration layer would hide ownership. | Spring bean declarations; preserve names/conditions and packages. |
| `taf-mcp-jobs`, `com.codinglair.taf.mcp.jobs` | `Job.text(...)` (10, line 58) / `JobEvent.bounded(...)` (10, line 17) | Exact bounded text validation. Low payoff; retain unless a local existing validator is already being reused. | Private helpers in public records; constructors/contracts unchanged. |
| `examples/apple-appium-consumer` and `taf-mcp-tools` resource blueprint; `com.example.apple.*` / `__BASE_PACKAGE__.*` | Matching `AppleObjectFactory.start` (16), `MobileConfiguration.interactionPreflight` (22), `initialize` (12), `InteractionTask.verify` (16), constructors and composition/task tests | Template/example copies are expected golden-consumer correspondence. Keep explicit; do not link generated consumer projects back to framework-internal test/source utilities. | Consumer/sample/template package substitution remains intact. |

Other duplicate test-fixture methods are retained in the screening index. Small repository stubs, synthetic failures, and test-local configurations do not by themselves justify a shared test-support module. Copy detection is evidence of sameness, not an automatic consolidation assignment.

## Length and nesting candidates that do not automatically warrant refactoring

| Example | Decision |
| --- | --- |
| `BlueprintCompositionEngine.Request` / `NormalizedRequest` constructors (30-34 lines), `ToolRequest` constructor (28), `McpResourceService` constructor (27) | Mostly argument forwarding/assignment; preserve explicit compatibility constructors. Splitting would obscure the contract. |
| `CapabilityContributionCatalog.pom()` (52), `MobileBlueprintAssets.assets()` (67), `AsyncApiContractAdapter.generate()` / `OpenApiContractAdapter.generate()` (34 each) | Declarative template/catalog construction inflates physical length. No generic renderer or catalog abstraction proposed. |
| `AllureReporter.reportFailure()` (47) | Straight-line label mapping. Optional use of an already existing local label helper if warranted later; length alone is insufficient. |
| `InputSizeValidator.inspect()` (34) | Cohesive recursive type switch, with depth/element/byte limits clearly owned together. Retain rather than move every case to a helper. |
| `DefaultObservabilityController.assertEventually()` (35) | A readable, cohesive polling operation despite a try/catch and loop. No new shared polling layer. |
| `ControllerRegistry.close()` (18), `ContainerLifecycleCoordinator.close()` (20), `BoundedHttpBody.read()` (18), `ReportingContext.execute()` (16) | Nesting largely reflects necessary resource/error ownership; do not flatten finally/locking scopes mechanically. |
| `EnvironmentRegistryTest.concurrentRegistrationHasExactlyOneWinner()` (34) | Nested callbacks directly express the concurrency assertion. Retain unless a test-local helper clearly improves intent; do not hide the concurrent registration operation. |
| Large integration tests and literal-heavy tests in the screening index | Scenario length is not a finding by itself. Retain the arrange/act/assert narrative; extract only distracting infrastructure or repeated assertion groups after reading the scenario. |

## Maven/package coverage and full candidate index

The following coverage table groups all parsed declarations by their nearest `pom.xml`. Root-owned files include source-launched utilities and Java examples outside standard source roots; this does not turn them into published root artifacts. Standalone consumer POMs and Java assets were scanned even where not part of the default reactor. Packages and exact source locations are in the accompanying CSV.

| Maven owner | Java files with bodies | Methods / constructors | Over 20 lines | Depth >= 3 | Actionable rows |
| --- | ---: | ---: | ---: | ---: | ---: |
| `root / build support and nonstandard source assets` | 29 | 199 | 36 | 12 | 6 |
| `blueprints/playwright-consumer-v1/template` | 17 | 33 | 0 | 0 | 0 |
| `codinglair-taf-common` | 23 | 93 | 3 | 0 | 0 |
| `codinglair-taf-mcp` | 1 | 1 | 0 | 0 | 0 |
| `codinglair-taf-runtime/codinglair-taf-reporting-allure` | 16 | 112 | 14 | 3 | 3 |
| `codinglair-taf-runtime/codinglair-taf-runner-cucumber` | 13 | 89 | 10 | 2 | 3 |
| `codinglair-taf-runtime/codinglair-taf-runner-testng` | 10 | 180 | 15 | 4 | 3 |
| `codinglair-taf-runtime/codinglair-taf-runtime-core` | 87 | 716 | 86 | 21 | 9 |
| `codinglair-taf-runtime/taf-api-rest` | 11 | 92 | 5 | 1 | 2 |
| `codinglair-taf-runtime/taf-api-soap` | 20 | 117 | 13 | 2 | 2 |
| `codinglair-taf-runtime/taf-consumer-conformance` | 12 | 115 | 30 | 8 | 2 |
| `codinglair-taf-runtime/taf-contracts` | 17 | 61 | 7 | 1 | 0 |
| `codinglair-taf-runtime/taf-data-migration` | 23 | 166 | 21 | 1 | 1 |
| `codinglair-taf-runtime/taf-database` | 18 | 139 | 15 | 7 | 2 |
| `codinglair-taf-runtime/taf-environments` | 52 | 287 | 27 | 7 | 2 |
| `codinglair-taf-runtime/taf-file` | 15 | 88 | 11 | 6 | 2 |
| `codinglair-taf-runtime/taf-messaging-aws` | 53 | 379 | 42 | 14 | 4 |
| `codinglair-taf-runtime/taf-messaging-core` | 11 | 51 | 1 | 1 | 0 |
| `codinglair-taf-runtime/taf-messaging-jms` | 10 | 77 | 6 | 2 | 1 |
| `codinglair-taf-runtime/taf-messaging-kafka` | 13 | 104 | 12 | 3 | 2 |
| `codinglair-taf-runtime/taf-messaging-rabbitmq` | 13 | 103 | 9 | 1 | 1 |
| `codinglair-taf-runtime/taf-mobile-appium` | 41 | 440 | 67 | 15 | 10 |
| `codinglair-taf-runtime/taf-mobile-core` | 4 | 11 | 3 | 1 | 0 |
| `codinglair-taf-runtime/taf-observability` | 12 | 57 | 9 | 1 | 0 |
| `codinglair-taf-runtime/taf-secrets-api` | 4 | 22 | 2 | 0 | 0 |
| `codinglair-taf-runtime/taf-secrets-local` | 15 | 86 | 10 | 2 | 2 |
| `codinglair-taf-runtime/taf-test-definitions` | 33 | 135 | 18 | 9 | 3 |
| `codinglair-taf-runtime/taf-test-definitions-mongodb` | 12 | 84 | 9 | 4 | 1 |
| `codinglair-taf-runtime/taf-virtualization-wiremock` | 14 | 80 | 11 | 1 | 0 |
| `codinglair-taf-runtime/taf-web-playwright` | 10 | 133 | 8 | 3 | 2 |
| `demos/playwright-sauce-demo` | 33 | 101 | 10 | 1 | 1 |
| `examples/apple-appium-consumer` | 11 | 37 | 1 | 1 | 0 |
| `release/consumer-smoke/aws-messaging` | 1 | 8 | 2 | 0 | 0 |
| `release/consumer-smoke/mcp-http` | 1 | 1 | 0 | 0 | 0 |
| `release/consumer-smoke/runtime-core` | 1 | 1 | 0 | 0 | 0 |
| `release/consumer-smoke/starter-api` | 1 | 1 | 0 | 0 | 0 |
| `release/consumer-smoke/starter-database` | 1 | 1 | 0 | 0 | 0 |
| `release/consumer-smoke/starter-maximal` | 1 | 2 | 1 | 0 | 0 |
| `release/consumer-smoke/starter-messaging` | 1 | 1 | 0 | 0 | 0 |
| `release/consumer-smoke/starter-messaging-aws` | 1 | 1 | 1 | 0 | 0 |
| `release/consumer-smoke/starter-messaging-jms` | 1 | 1 | 1 | 0 | 0 |
| `release/consumer-smoke/starter-messaging-kafka` | 1 | 3 | 0 | 0 | 0 |
| `release/consumer-smoke/starter-messaging-rabbitmq` | 1 | 1 | 0 | 0 | 0 |
| `release/consumer-smoke/starter-mobile` | 1 | 1 | 0 | 0 | 0 |
| `release/consumer-smoke/starter-web` | 1 | 1 | 0 | 0 | 0 |
| `release/consumer-smoke/web-playwright` | 1 | 1 | 0 | 0 | 0 |
| `taf-mcp-server/taf-execution-worker` | 14 | 66 | 13 | 3 | 1 |
| `taf-mcp-server/taf-mcp-contracts` | 2 | 13 | 1 | 0 | 0 |
| `taf-mcp-server/taf-mcp-jobs` | 14 | 61 | 12 | 5 | 1 |
| `taf-mcp-server/taf-mcp-prompts` | 9 | 44 | 6 | 0 | 0 |
| `taf-mcp-server/taf-mcp-resources` | 19 | 73 | 10 | 0 | 0 |
| `taf-mcp-server/taf-mcp-security` | 19 | 77 | 11 | 1 | 1 |
| `taf-mcp-server/taf-mcp-tools` | 46 | 310 | 54 | 12 | 8 |
| `taf-mcp-server/taf-mcp-transport-http` | 20 | 148 | 11 | 3 | 1 |
| `taf-mcp-server/taf-mcp-transport-stdio` | 14 | 88 | 9 | 5 | 1 |

POM-only parents, BOM and capability starters have no method bodies to assess. A zero in the last column means no selected actionable refactoring here, not a blanket maintainability or correctness certification.

## Verification, limitations and next assignments

Executed read-only `git status --short`, `git rev-parse HEAD`, `rg --files` Java/module discovery, `rg -n` rule/contract searches, PowerShell source reads, and an ephemeral Java 25 source-launch scan using `JavacTask.parse`, `Trees.getSourcePositions` and `TreeScanner`. The scan reported 997 files / 5,292 declarations, with no parser diagnostics. Candidate extraction and repeated-body screening used PowerShell over the ephemeral TSV. The scanner and raw method bodies stay in the OS temporary directory; only this report and the source-location index are repository changes.

Report validation checks every selected finding resolves to its scanned class/method, source links exist, CSV keys/counts are consistent, and `git diff --check` reports no whitespace errors. Production/test suites were not executed because no executable code changed; no runtime pass or behavior equivalence is claimed. Exact future verification should be recorded by each implementation assignment, typically `.\mvnw.cmd -pl <module> -am test`, followed by affected contracts/vertical smoke and architecture/dependency gates. Browser, Appium, broker, database or container qualification remains required when the future change affects those behaviors.

Begin with one P1 workflow and its current tests, approve a narrow behavior-preserving extraction, and review its diff before assigning neighboring methods. No implementation, contract change, package move, dependency approval, commit, push or publication is included in this discovery. No material authority conflict blocks the report. Remaining limits are heuristic clone detection, parse-only visibility/type information, and manual prioritization rather than a proof that every screened method should change.

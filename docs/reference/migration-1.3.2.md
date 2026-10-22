# Migrating consumers to the 1.3.2 corrective contract

This guide describes the corrective contract prepared for 1.3.2. The root Maven revision remains
the current-version authority; this release-target label does not promote or publish 1.3.2.

## Separate automation from the SUT

Move automation to its own Maven build root with its own wrapper, POM, dependencies, execution and
reports. It may be a sibling of the SUT or a nested directory in the same repository, but remove it
from the SUT reactor and remove every TAF BOM, dependency, source, configuration or packaged class
from production. Run it as a separate process against supported external SUT interfaces.

For official scaffolding, send explicit `automationRoot` and `sutRoot`. The compatible
`destination` alias remains accepted for `automationRoot`. Existing descriptor 1.0 and 1.1 files
remain readable, but governed execution requires migration to descriptor 1.2 with
`project.type: standalone-test-automation`, `project.expectedBuildRoot: .`,
`project.sutInteraction: external` and the TestNG runner. Do not work around a containment error by
editing an ancestor build.

## Adopt the governed runner and lifecycle

Ordinary consumer tests use TestNG. Extend `TafBaseTest`, explicitly select Surefire's TestNG
provider, register the TAF TestNG integration and bind both `validate` and
`validate-compiled-tests` goals of `taf-consumer-conformance-maven-plugin`. Cucumber remains
supported through `AbstractCucumberRunner`, separate suite selection and the framework glue that
installs `TafCucumberHooks`.

Remove JUnit engines and mixed discovery from the automation build, custom TestNG/Cucumber hooks
that create sessions, and consumer-created `TestSession` lifecycle. Keep SUT unit tests in the
independent SUT build. TAF's own implementation tests may continue to use JUnit because they do
not represent supported consumer execution.

Every capability call now requires the active invocation-owned session. Missing, closed, stale or
wrong-owner access fails before side effects with `TAF-LIFECYCLE-001`; there is no fallback session.
Do not retain controllers or vendor-native handles beyond their invocation.

## Preserve formats while accepting serializer ownership changes

No consumer format conversion is required for supported JSON, YAML or CSV inputs. TAF-owned
JSON/YAML implementations are module-local and use Jackson 3 where migrated. The remaining
Jackson 2 CSV, Mongo, migration, AWS, conformance and MCP call sites are explicitly bounded and
owned by SER-133-001. `jackson-annotations` 2.21 is intentionally shared with Jackson 3 and does not
indicate a second Jackson runtime.

Do not exclude TAF-managed Jackson artifacts, override their versions or register a TAF mapper in
the SUT. The legacy `CsvFileDataProvider` behavior remains available with optional, non-exporting
Jackson 2 CSV implementation dependencies. Existing Mongo mapper-taking implementation signatures
are the only recorded public-signature exceptions.

## Requalify evidence

Consumer code uses reporter-neutral TAF annotations. REST request/response, status, timing,
transport-failure and assertion diagnostics pass through `ArtifactCollector`; no capability calls
Allure directly. Evidence metadata and payloads are independently redacted and size-bounded.
Visual evidence requires an explicit capability policy; text redaction does not authorize pixels.

Retain raw results, the single-file report, expectation matrix, validator result and candidate
manifest together. Automated validation does not replace the release owner's offline readability
review. The completed local candidate covered Boot 3.5.7 and Boot 4.0.1 external SUTs, 18 isolated
consumer projects/61 tests, 26 runner and boundary scenarios, and TestNG/Cucumber/no-Allure report
paths. Full browser/device matrices, external brokers, hosted CI reruns and manual readability are
owner-managed and must not be inferred from those deterministic fixtures.

See [standalone automation](standalone-automation-boundary.md),
[TestNG conformance](testng-consumer-conformance.md),
[Jackson remediation](jackson-dependency-remediation-1.3.2.md), and
[evidence validation](consumer-evidence-validation.md) for executable details.

# Consumer Conformance and Migration Checklist

Use this checklist with the executable `taf-consumer-conformance` gate for every generated or
migrated project. A checked item requires build evidence; compilation alone is insufficient.

## Build and execution evidence

- Project descriptor validates at governed schema 1.2, declares
  `project.type: standalone-test-automation`, an external SUT interaction model, TestNG, and one
  authoritative provider. Schema 1.0/1.1 inputs are migration sources, not governed-run results.
- The automation project owns its Maven wrapper and POM, is absent from the SUT reactor and
  production graph, and builds/runs as a separate process even when both roots share a repository.
- Only selected starters/runners and their vendor libraries resolve in the dependency tree.
- Local and CI Spring contexts load; unselected auto-configurations and beans remain absent.
- Typed named configuration binds, and unresolved values fail one aggregated `ConsumerPreflight`
  before controller initialization without exposing values.
- TestNG methods use `TafBaseTest`; Cucumber scenarios use `TafCucumberHooks`; observers create no
  session. Parallel, failed, cancelled, and partially initialized invocations clean up exactly once.
- The POM explicitly selects the Surefire TestNG provider and binds both consumer-conformance
  goals. JUnit-only, mixed-engine, missing-integration, and copied lifecycle ownership fail before
  a test body runs; JUnit remains permitted only in TAF's separate implementation-test builds.
- Traceability identifiers are unique and resolve to typed input and expected-output definitions.
- Neutral and adapter output preserves audience-appropriate hierarchy, unique event identity,
  duplicate suppression, validation context, and sanitized artifact references.
- Capability evidence crosses `ArtifactCollector`, not an Allure API. The evidence policy records
  independent metadata/payload redaction, size bounds, visual allow/omit decisions, attachment
  ownership before result closure, and machine plus manual report qualification.

## Subject-aware abstraction evidence

Record the requirement/test condition that identifies the subject under test. Confirm that repeated
prerequisite flows use a readable workflow/service where appropriate and that page, component, API,
messaging, repository, or controller actions remain explicit when they are the behavior under test.
When the subject is uncertain, preserve explicit actions and request review. This is semantic evidence,
not a validator failure and not a universal workflow-consolidation rule.

## Migration compatibility matrix

For every demonstrated source capability, record evidence, `preserve`, `approved replacement`, or
`approved retirement`, the target contract, and an executable verification. Structure,
configuration, data, traceability, lifecycle, reporting, cleanup, parallel behavior, and controlled
failure evidence may not disappear implicitly. A retirement needs an accepted ADR and maintainer
review.

## Capability gaps

Stop consumer implementation when the versioned descriptor/capability manifest cannot express the
required design, a selected Runtime capability is unavailable, or conformance would require copying
framework lifecycle/controller code, bypassing sanitization/reporting, or changing a public Runtime
contract. Do not fabricate a local replacement. Open a public issue that identifies:

1. the requirement and unavailable capability or blueprint expression;
2. the attempted supported selections and sanitized validation evidence;
3. the smallest proposed framework or blueprint change and affected public contract;
4. work that remains safe and work intentionally not performed;
5. the decision or dependency needed to proceed.

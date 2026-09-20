# ADR-030: Gate Releases with External Starter and Blueprint Conformance

- **Status:** Accepted
- **Date:** 2026-09-14
- **Target release:** Codinglair TAF 1.2.0
- **Related requirements:** FR-CON-001–FR-CON-007; FR-DOC-003; NFR-019–NFR-021

## Context

In-reactor examples inherit Codinglair build configuration and can hide missing published metadata. Testing only the maximal starter graph can also hide a missing transitive dependency supplied accidentally by another starter.

## Decision

Extend the existing consumer-smoke/conformance process with clean projects that do not inherit the Codinglair parent and do not resolve reactor artifacts:

1. test each of the five top-level starters individually;
2. test each messaging provider starter individually;
3. test all five top-level starters plus all messaging provider starters together;
4. generate and execute a representative Web + API + Database project;
5. retain supported direct-module compatibility coverage;
6. syntactically validate every published Maven and Gradle dependency declaration.

The maximal-graph test validates convergence, duplicate auto-configuration, inactive messaging providers, provider library conflicts, bean ambiguity, context startup, and shared lifecycle/reporting behavior. Individual tests prove starter completeness. The generated project proves blueprint behavior. Exhaustive top-level starter combinations are not release-gating unless a specific interaction risk is identified.

Gradle validation covers dependency consumption syntax only. Release 1.2.0 does not promise Gradle scaffolding or Maven-plugin equivalence.

## Consequences

- Missing POM metadata and stale documentation fail before release.
- The bounded matrix provides complementary coverage without combinatorial expansion.
- Published artifacts must be staged in a repository accessible to conformance before promotion.

## Alternatives considered

- Test only all starters together: rejected because it can mask missing transitives.
- Test all 31 combinations: rejected without evidence of proportional risk.
- Rely on reactor examples: rejected because they do not represent an independent consumer.

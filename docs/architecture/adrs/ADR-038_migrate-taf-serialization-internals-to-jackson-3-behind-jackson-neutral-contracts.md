# ADR-038: Migrate TAF Serialization Internals to Jackson 3 Behind Jackson-Neutral Contracts

- **Status:** Accepted
- **Date:** 2026-10-18
- **Decision owners:** Codinglair TAF Architecture
- **Applies to:** TAF common/runtime modules, capability modules, starters, MCP distributions and TAF 1.3.2+

## Context

The TAF 1.3.1 dependency tree shows `codinglair-taf-common` and related modules exporting Jackson 2 core, databind, CSV and YAML implementations. Spring Boot 4 and current MCP dependencies use Jackson 3. The broad Jackson 2 graph caused vulnerability findings in a consumer project and creates two independently configured serialization stacks.

Jackson 3 intentionally continues using `com.fasterxml.jackson.core:jackson-annotations` 2.x. The annotations artifact is therefore not itself evidence of an incompatible Jackson 2 runtime. The architectural problem is exported Jackson 2 core, databind and data-format implementations and Jackson-coupled public contracts.

## Decision

Remediation is staged:

1. **Immediate correction:** converge Jackson 2 runtime and data-format artifacts on a non-vulnerable, mutually compatible supported baseline; manage the versions in the TAF BOM; scan every published consumption surface.
2. **Structural correction:** remove serialization implementations from `taf-common` and other shared contracts. JSON, YAML and CSV implementations belong to the narrow modules that use them.
3. **Target implementation:** migrate TAF-owned serialization code to Jackson 3 `tools.jackson` APIs and compatible third-party libraries.

Public TAF contracts shall not accept or return Jackson mapper, tree, parser, module or type-reference classes. They use Java records/POJOs, collections, primitives, strings, bytes, paths or streams. `jackson-annotations` 2.x remains permitted and is managed as one converged BOM version.

TAF automation remains a separate process from the SUT. TAF shall not register its mapper as the SUT application's primary mapper. A temporary Jackson 2 exception is permitted only when a required third-party library lacks Jackson 3 support; it must be isolated, non-exporting where technically possible, documented and time-bounded.

## Consequences

### Positive

- TAF aligns its implementation direction with Spring Boot 4.
- Boot 3 and non-Spring SUTs remain compatible through process and protocol boundaries.
- Consumers are not coupled to TAF's mapper implementation.
- Dependency and vulnerability exposure becomes narrower and auditable.

### Negative

- Jackson 2 patching and Jackson 3 migration may require separate assignments and verification cycles.
- Some libraries may temporarily retain an isolated Jackson 2 runtime.
- Serialization behavior must be regression-tested across JSON, YAML, CSV, schema validation and MCP paths.

## Alternatives Considered

### Retain Jackson 2 indefinitely

Rejected because Boot 4 support is transitional and consumer security scans already failed.

### Require consumers to exclude Jackson 2

Rejected because TAF code compiled against excluded classes may fail at runtime and the framework owns its transitive dependency model.

### Expose Jackson 2 and Jackson 3 APIs publicly

Rejected because it creates incompatible consumer contracts and permanent dual-stack coupling.

## Verification

- Maven dependency convergence passes across all starters, direct modules, maximal graphs and MCP distributions.
- Trivy and SBOM policy pass for each published surface and released-artifact consumer.
- Shared/public API checks reject Jackson runtime types.
- JSON, YAML, CSV, schema and MCP compatibility tests preserve expected behavior.
- A Boot 3 SUT and a Boot 4 SUT are each tested externally by the standalone automation project without mapper sharing.


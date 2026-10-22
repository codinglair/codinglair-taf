# DOC-132-001 documentation disposition inventory

Inventory date: 2026-10-22. Candidate basis: the prepared checkout plus DOC-132-001 changes.

## Method and exhaustive scope

The baseline inventory was derived from `git ls-files`; the two documentation files added by this
assignment are included explicitly, and ignored build output is not a tracked documentation
surface. The rules below are mutually exclusive in listed order, so every selected file has one
disposition. The selected universe contains 179 tracked/candidate prose/schema/example surfaces, 698
production Java sources whose public comments feed Javadoc, and 10 Spring configuration-metadata
files: 887 files in total.

| Order | Matching rule | Count | Classification and disposition |
| --- | --- | ---: | --- |
| 1 | `docs/{assignments,decisions,qualification,releases}/**` | 13 | Historical: retain immutable facts and version labels; do not synchronize or rewrite. |
| 2 | `build-support/local/README.md`, `docs/engineering/kiss-refactoring-discovery.md`, `release/qe-132-002/readability-checklist.md` | 3 | Excluded/internal evidence: retain; not consumer guidance. The checklist remains an owner action. |
| 3 | `blueprints/**`, `docs/examples/**`, `release/{consumer-smoke,sut-boundary}/**`, and public MCP resource/schema files under `taf-mcp-server/**/src/main/resources/**` | 26 | Current executable/template documentation: validate through docs and schema/consumer gates; edit only on contract drift. |
| 4 | All remaining selected README/CHANGELOG/SECURITY/CONTRIBUTING files, Markdown/AsciiDoc/reStructuredText, and documentation JSON/YAML/XML, including the two DOC-132-001 additions | 137 | Current documentation: root revision markers synchronize; reconcile affected public surfaces and retain unaffected capability guides. |
| 5 | `**/src/main/java/**/*.java` | 698 | Current generated API documentation source: preserve public comments; API/Javadoc verification covers them. No corrective contract comment remained stale. |
| 6 | `**/src/main/resources/META-INF/*spring-configuration-metadata.json` | 10 | Current generated-help metadata: preserve and validate with module/docs builds; no corrective configuration key changed. |
| 7 | Tracked generated output | 0 | Generated: no tracked `target` or generated-site output exists; regenerate during verification rather than commit it. |

The 179-file selection includes repository/root and module/example READMEs; public architecture and
ADRs; reference, reporting, operations, security and release guides; blueprint/schema examples;
workflow help; and consumer/MCP schema descriptions. Planning assignments, handoffs and reviews are
not tracked in this prepared checkout and therefore are dependency evidence rather than members of
the tracked-file count. The required DOC-132-001 handoff is still written at its prescribed path.

## Corrective-release impact disposition

| Surface | Disposition |
| --- | --- |
| Root overview, public solution architecture and compatibility matrix | Updated for standalone topology, TestNG/Cucumber/JUnit boundary, active-session failure and Jackson-neutral contracts. |
| Quick Start, consumer checklist and reference index | Updated for independent roots, descriptor 1.2 migration, provider/conformance bindings, and the prohibition on consumer Jackson exclusions/mapper overrides. |
| Standalone, TestNG, Jackson, Runtime and evidence guides | Retained as current detailed records; reconciled by the new integrated migration guide. |
| ADR-036 through ADR-038 | Retained as accepted public decisions and linked from public architecture. |
| CHANGELOG | Updated with actual candidate behavior, executed tuple/count evidence and explicit unrun owner gates. |
| Capability/module/example guides not named above | Inspected by focused searches and docs gates; retained because the corrective work did not change their capability API/configuration. |
| Historical release, qualification and assignment evidence | Preserved without dynamic version markers or rewritten claims. |

## Version and evidence rules

Only explicitly marked documentation version values are synchronized with the current root revision. Immutable release labels remain literal and must not be automatically updated. The 1.3.2 heading and migration filename identify the target corrective release, not the
current Maven revision. Qualification claims are limited to the completed QE/VER handoffs; hosted
CI, full browser/device/external-broker modes and manual report readability remain unrun or
owner-managed.

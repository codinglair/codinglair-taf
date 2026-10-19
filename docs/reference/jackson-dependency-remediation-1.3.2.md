# Jackson dependency remediation for 1.3.2

This record is the DEP-132-001 inventory, vulnerability rationale, publication-surface plan and
temporary-exception register. The retained machine evidence is generated under
`target/dep-132-001/`; it is build output and is not committed.

## Selected baseline

TAF manages Jackson 2 runtime and data-format artifacts at `2.21.7`, Jackson 3 runtime and YAML
artifacts at `3.1.7`, and
`com.fasterxml.jackson.core:jackson-annotations` at `2.21`. Jackson changed annotations to
major/minor-only versioning in 2.20, so `2.21` is the matching annotations release rather than a
version divergence. The 2.21 line is Jackson's open LTS line through at least January 2028.
Patch 2.21.7 includes the September 2026 fixes for CVE-2026-91776 and CVE-2026-91777 and includes
the earlier 2.21 fixes for CVE-2026-19032, CVE-2026-54512 through CVE-2026-54518,
CVE-2026-59888, CVE-2026-59889, CVE-2026-68497, CVE-2026-77310 and CVE-2026-83557.
Jackson 3.1.7 is the patched 3.1 LTS release for the September 2026 databind advisories
CVE-2026-91776 and CVE-2026-91777. It also supersedes the 3.1.6 fixes, including
CVE-2026-68497.

Primary compatibility and remediation evidence:

- [Jackson supported-release policy and 2.21 LTS status](https://github.com/FasterXML/jackson/wiki/Jackson-Releases)
- [Jackson 2.21 release and patch history](https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.21)
- [Jackson databind 2.x release notes](https://github.com/FasterXML/jackson-databind/blob/3.x/release-notes/VERSION-2.x)
- [Jackson 3.1.7 release record](https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.1.7)
- [CVE-2026-91776 upstream advisory](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-wv8q-qhhj-9h54)
- [CVE-2026-91777 upstream advisory](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-cxp5-3px4-pw24)

The original consumer scanner JSON was not available in the prepared checkout. Consequently, no
scanner/database attribution is invented for the reported 1.3.1 findings. The reproducible
baseline is revision `422530cddec38c5c053306ad88f372b005a5358e`, whose root POM managed Jackson
2.19.2. A baseline scan must be retained if the owner obtains the original report or checks out
that revision in an approved scanning environment. Spring Boot 4.1.1 also resolved Jackson 3.1.5
at that baseline; the staged scan demonstrated that it required convergence to 3.1.7.

## Direct implementation inventory

| Introducing module | Jackson use at the 1.3.1 baseline | 1.3.2 management |
| --- | --- | --- |
| `codinglair-taf-common` | databind, annotations, CSV | runtime/data format 2.21.7; annotations 2.21 |
| `taf-test-definitions` | CSV and YAML | 2.21.7 |
| `taf-test-definitions-mongodb` | databind | 2.21.7 |
| `taf-file` | databind | 2.21.7 |
| `taf-contracts` | databind and YAML | 2.21.7 |
| `taf-data-migration` | databind | 2.21.7 |
| `taf-messaging-aws` | databind; validator YAML excluded | 2.21.7 |
| `taf-consumer-conformance` | databind for conformance implementation/tests | 2.21.7 |
| `taf-mcp-resources` | databind | 2.21.7 |
| `taf-mcp-contracts` | test-only databind | 2.21.7 |
| MCP STDIO and HTTP transports | Jackson 3 plus compatible annotations | Jackson 3.1.7 and annotations 2.21 from the TAF BOM; no local override |
| REST/schema validators | validator-owned transitive graph; YAML excluded where already required | resolved graph and staged SBOM gate |
| Cucumber runner/tool graph | Cucumber 7.17.0 embedded relocated Jackson 2.17.0 classes that BOM management could not override | Cucumber 7.34.9, whose embedded Jackson BOM is patched at 2.22.3 |

The root dependency management and the published `codinglair-taf-bom` explicitly manage core,
Jackson 2 core, databind, annotations, CSV and YAML plus Jackson 3 core, databind and YAML. This
ensures that a standalone consumer receives the same
constraints without inheriting the TAF parent or depending on Spring Boot BOM ordering.

## Publication-surface evidence

The release-staging build attaches a CycloneDX SBOM to every published JAR. The pinned
`aquasec/trivy:0.72.0` scan script discovers every staged `*-cyclonedx.json`, rather than using a
hand-maintained allowlist, and writes one row per surface to
`target/dep-132-001/surface-evidence.tsv`. Each row contains the SBOM SHA-256, Jackson component
count, HIGH/CRITICAL Jackson finding count, unrelated HIGH/CRITICAL finding count and retained
report. The gate rejects every unapproved Jackson finding; unrelated findings remain visible in
the JSON and summary and are not suppressed.
The release contract also rejects Jackson classes bundled or shaded inside a published TAF JAR.

Resolved clean-consumer trees and effective POMs are retained by the `consumer-smoke` profile for:

- every top-level starter and every messaging provider starter;
- the maximal starter/provider graph;
- direct runtime-core, web, mobile and AWS messaging consumers; and
- the MCP HTTP distribution.

All other supported direct modules and MCP publication units receive an exhaustive staged-SBOM
and scanner row. Final released-artifact expansion remains owned by VER-132-001, as mapped by the
requirements matrix.

## Temporary Jackson 2 exception register

| Path | Introducing owner | Constraint | Expiry/removal owner |
| --- | --- | --- | --- |
| TAF-owned JSON/YAML/CSV implementation modules listed above | Existing TAF implementation code | Patched BOM-managed 2.21.7 only; no bundled copy or consumer exclusion; public contracts must not gain mapper/tree/parser types | SER-132-001 structural slice, with remaining enumerated migration in SER-133-001 |
| Jackson 2 annotations used by Jackson 3 consumers | Jackson 3 compatibility contract | Exactly one BOM-managed 2.21 annotations artifact; annotations alone are not a Jackson 2 runtime | Permanent compatibility allowance under ADR-038; reassess with Jackson upstream |

## SER-132-001 serializer ownership and compatibility

| Format/use | Implementation owner after SER-132-001 | Runtime | Compatibility disposition |
| --- | --- | --- | --- |
| JSON/YAML versioned file definitions | `taf-test-definitions` | Jackson 3.1.7 | Public repository/configuration contracts remain Java-only; serialized schema, ordering and defaults are covered by the repository contract corpus |
| JSON structured-file comparison | `taf-file` | Jackson 3.1.7 | Comparator contracts remain Java-only; null, numeric and collection/tree behavior remains format-local |
| OpenAPI/AsyncAPI JSON/YAML parsing | `taf-contracts` | Jackson 3.1.7 | Jackson tree parameters were reduced from protected to package-private implementation hooks; public adapter contracts are unchanged |
| CSV secret-reference normalization | `taf-test-definitions` | Patched Jackson 2.21.7 CSV | Jackson 3.1.7 does not provide the CSV artifact in the approved dependency set; quoting, headers, line endings, invalid input and secret-reference safety remain covered here pending SER-133-001 |
| Legacy `CsvFileDataProvider` | `codinglair-taf-common` compatibility facade | Patched Jackson 2.21.7 CSV, optional/non-exporting | Class, constructors and behavior are retained; mapper and CSV dependencies are optional so common no longer exports them transitively. Removal or package relocation requires a separately approved compatibility disposition |

TAF creates these mappers directly inside their owning implementation classes. None is a Spring
bean and none can become the SUT application's primary mapper.

### Exact SER-133-001 remainder

The following TAF-owned Jackson 2 call sites are the complete post-SER-132-001 remainder. They are
not a reason to export Jackson from common and may not grow:

- `codinglair-taf-common/.../CsvFileDataProvider.java`: legacy public compatibility implementation;
- `taf-test-definitions/.../CsvSecretNormalizer.java`: CSV data-format implementation unavailable
  in the approved Jackson 3 set;
- `taf-test-definitions-mongodb`: repository/synchronizer private implementation plus three
  existing mapper-injection constructor signatures;
- `taf-data-migration`: Flyway structured output and Mongo migration implementation, including the
  existing `MongoContextMigration.containerized` mapper parameter;
- `taf-messaging-aws`, `taf-consumer-conformance`, `taf-mcp-resources`, and MCP contract tests:
  separately owned JSON implementation/test call sites recorded by DEP-132-001.

SER-133-001 owns migration or architecture-approved compatibility disposition of this exact list.
The public-signature gate contains explicit entries only for the three pre-existing Mongo
implementation signatures; every other supported public/protected contract is rejected if a
Jackson runtime type appears.

No third-party runtime is approved to pin an older Jackson 2 version. A future exception requires
an introducing path, owner, isolation proof, expiry and removal assignment in this table.

The Cucumber upgrade retains the existing MIT license and 7.x public line. Its purpose is to
remove the scanner-visible, relocated Jackson 2.17.0 copy; risk is bounded by the TestNG
orchestration, TAF hook, scenario-session ownership and reporting integration suites.

## Approved MCP HttpCore security exception

The MCP STDIO distribution necessarily retains Testcontainers 2.0.5 and its working
`docker-java-transport-zerodep:3.7.1` transport. Replacing that transport caused provisioning and
AWS/LocalStack regressions. Removing it would remove supported `taf-environments` container
provisioning, contrary to ADR-007. The owner approved this temporary disposition on 2026-10-18;
it is a genuine residual vulnerability, not a false positive.

| CVE | Embedded package | Exact containing distribution/path | Reachability and residual risk | Owner / review / expiry |
| --- | --- | --- | --- | --- |
| CVE-2026-54399 | `httpcore5:5.3.6` relocated under `com.github.dockerjava.zerodep.shaded.org.apache.hc.core5`; HTTP/1 parser classes include `AbstractMessageParser` and `DefaultHttpRequestParser` | MCP image STDIO classpath only, `/opt/taf/stdio/lib/docker-java-transport-zerodep-3.7.1.jar` | Reachable when container provisioning parses responses from the configured Docker endpoint. A malicious or compromised daemon/proxy can cause memory exhaustion with excessive headers. | TAF Security/Release Owner; review 2026-11-18; expires 2026-12-18 or the 1.3.3 release gate, whichever is first |
| CVE-2026-54428 | `httpcore5-h2:5.3.6` relocated under the same prefix; HTTP/2 classes include `HPackDecoder` | Same MCP STDIO JAR/path | Classes are present, but the zerodep transport constructs the classic HTTP client and no HTTP/2 requester path was identified. The packaged finding remains accepted as genuine. | Same owner, review, and expiry |

The dependency path is `taf-mcp-transport-stdio -> taf-mcp-resources -> taf-environments ->
testcontainers:2.0.5 -> docker-java-transport-zerodep:3.7.1`. A Maven BOM override cannot replace
relocated classes embedded in that JAR. The JAR SHA-256 is
`b89bdb1754160323597f9ea32a7fe7a4a3aa8f5b3b43b88e8d71fff3b267ab21`.

During the exception, operators must use a trusted local Unix socket or Windows named pipe, or an
administratively controlled mutually authenticated TLS Docker endpoint. Plaintext or untrusted
remote Docker endpoints are prohibited. Docker endpoint configuration is process/operator-owned
through Testcontainers/Docker configuration; MCP callers cannot supply it per request. Access to
the daemon remains privileged and must not be exposed to untrusted MCP clients.

The exception is removed when a compatible Testcontainers/docker-java release embeds HttpCore
5.4.3 or later and passes all provisioning/AWS qualification, or when a separately approved
architecture change removes provisioning from the MCP STDIO distribution. The gate matches the
exact CVE, package/version, containing artifact/version, path and `mcp` distribution. It preserves
the unsuppressed Trivy report and SBOM, emits accepted and unapproved reports separately, rejects
all unexpected findings, and fails after expiry.

## Reproduction

```text
.\mvnw.cmd -B -ntp -Pdependency-analysis verify
.\mvnw.cmd -B -ntp clean deploy -Prelease-staging
.\mvnw.cmd -B -ntp -N -Pconsumer-smoke verify
bash build-support/scripts/scan-jackson-surfaces.sh target/staging-repository target/dep-132-001
```

The scan downloads the pinned scanner image and current Trivy vulnerability databases. Retain
`trivy-version.txt`, every JSON report and `surface-evidence.tsv` together so scanner and database
provenance remain reviewable.

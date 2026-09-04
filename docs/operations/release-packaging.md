# Community release packaging and staging

DEVOPS-005 stages only the Apache-2.0 Runtime and MCP product lines. The proprietary Quality
Intelligence module and the Sauce Demo consumer are built by the normal reactor but have deploy
disabled when `release-staging` is active. They must never appear in a community staging repository.

## Version and artifact policy

- The literal root-POM `revision` property is the single source of truth for the project and
  container-image version. Reactor modules use Maven's CI-friendly ${revision} placeholder;
  container and Kind entry points extract that value and allow an explicit `TAF_IMAGE_VERSION`
  override for testing an alternate image tag.
- `codinglair-taf-bom` is the consumer BOM and aligns every supported Runtime capability and MCP
  artifact to one release version. Capability modules are the Spring Boot consumption entry points;
  this release does not invent separate aggregate starter artifacts.
- Public APIs and versioned MCP schemas remain backward compatible within a major release. Removal
  requires an approved ADR, at least one minor release of deprecation notice, and migration guidance.
- Every published JAR has a sources JAR, Javadoc JAR, CycloneDX JSON SBOM, POM license metadata, and
  Maven checksums. Every artifact deployed to Maven Central also has an ASCII-armored PGP signature.
  POM-packaged parents and the BOM publish their authoritative POM metadata.
- The reviewed commit is the release input. CI derives `project.build.outputTimestamp` from that
  commit, making archive timestamps deterministic. Rebuilding must use the same commit, Java 25,
  Maven Wrapper, repository settings/mirrors, and timestamp.

## Release checklist

1. Confirm the reviewed commit/tag and that the working tree used for local support evidence is clean.
2. Confirm the complete DEVOPS-002 release matrix and container/Kind gates succeeded for that exact SHA.
3. Run `./mvnw clean deploy -Prelease-staging` (on Windows, `mvnw.cmd`) with the commit timestamp as
   `project.build.outputTimestamp` when producing candidate evidence.
4. Run `./mvnw -Pconsumer-smoke verify`. Each Invoker project uses a fresh isolated local repository,
   imports the staged BOM, resolves one supported consumption boundary, and runs a test without
   framework test fixtures.
5. Inspect `target/staging-repository`: no Quality Intelligence or demo artifact; required sources,
   Javadoc, CycloneDX, license-bearing POM, and checksums are present.
6. Run API/schema compatibility, dependency/architecture, and license review gates. Reject unknown,
   prohibited, or incompatible licenses and any unapproved critical vulnerability.
7. Compare candidate artifact digests with a second build from the same commit in an equivalent
   clean environment. Record differences; do not promote a non-reproducible candidate.
8. Promotion to a public Maven/registry endpoint, signing, or tag creation is a separately authorized
   human action. The workflow retains staging evidence but does not autonomously publish.

## Maven Central publication

The `central-publish` profile is the complete Central publication path. It attaches sources,
Javadocs, and a CycloneDX JSON SBOM, signs every deployed artifact with `maven-gpg-plugin`, and hands
the deployment bundle to the Central Portal plugin. Do not combine it with `release-staging`; that
profile supplies a local `distributionManagement` repository and is only for unsigned candidate
evidence.

Before publication, configure the `central` server credentials in the maintainer or CI Maven
settings and make the release private key available to GnuPG. Supply the signing passphrase through
the Maven GPG plugin's supported secure environment/agent mechanism; never place it in the POM,
command line, workflow logs, or repository files. Then run:

```shell
./mvnw -B -ntp clean deploy -Pcentral-publish \
  -Dproject.build.outputTimestamp="$(git show -s --format=%cI HEAD)"
```

The Portal plugin waits for validation but leaves automatic publication disabled. Inspect the
validated deployment in Central Portal and publish it only after its POM metadata, sources,
Javadocs, SBOMs, and `.asc` signatures have been checked. Test the same profile before a release with
an ephemeral non-production signing key and the command below. It executes the real deploy lifecycle
and produces `target/central-publishing/central-bundle.zip`, but Sonatype's `skipPublishing` option
prevents an upload:

```shell
./mvnw -B -ntp clean deploy -Pcentral-publish -Dcentral.skipPublishing=true
```

Inspect the ZIP and verify that every POM, primary artifact, sources JAR, Javadoc JAR, and JSON SBOM
has a corresponding `.asc` signature. This dry run is the required profile/bundle check; remove the
override only for an authorized Central deployment.

## Dependency license policy

The verification-only release workflow runs `ReleaseLicensePolicy` against every staged CycloneDX
JSON SBOM. It classifies each declaration as `PERMISSIVE`, `CONDITIONALLY_ALLOWED`, `PROHIBITED`, or
`UNKNOWN_OR_AMBIGUOUS`; this is not a flat allowlist. Conditional licenses pass only for an exact
component/version, exact canonical expression, and recorded obligations. Plain GPL without the
Classpath Exception, AGPL, SSPL, BUSL, and proprietary licenses are prohibited.

Canonical SPDX identifiers are accepted directly. Non-SPDX aliases are deliberately narrow and
component-specific, with corroborating upstream URLs. Generic labels such as `GNU Lesser General
Public License`, `BSD licence`, and `CPL` fail unless that exact component rule supplies enough
metadata to establish a version. A long-form GPL declaration is treated as the Classpath Exception
only when the declaration URL identifies that exception. Missing, unknown, ambiguous, or
unclassified declarations fail even when another declaration looks permissive. For valid explicit
alternatives, the gate selects a permissive choice first and otherwise requires the exact recorded
conditional approval.

The reviewed inventory, selected expressions, evidence, and operational obligations are recorded
in [release-license-compliance.md](release-license-compliance.md). That record is an engineering
control, not legal advice or a claim that obligations have been discharged. Any coordinate,
version, raw declaration, URL, selected expression, packaging method, or dependency-graph change
requires policy review and an evidence update in the same change; the gate must not be bypassed.

## Rollback

Before public promotion, delete or expire the CI staging artifact and rerun from the corrected
reviewed commit. After publication, never overwrite released coordinates. Close/drop an open remote
staging repository, revoke or mark compromised signatures/attestations when applicable, publish a
new patch version, and attach migration/security guidance. If an image was promoted, remove the
mutable tag from promotion paths while retaining the immutable digest and audit evidence; do not
erase provenance needed for investigation.

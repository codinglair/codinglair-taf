# Codinglair TAF 1.3.0 release readiness

**Assessment date:** 2026-10-07

**Assignment:** REL-130-001

**Verdict:** **READY**

**Promotion status:** Eligible for release-owner evidence rebinding and authorized promotion.
Those operations remain outside REL-130-001.

This is a pre-publication readiness decision. It does not require a merge, tag, final master SHA,
public Maven artifacts, a public image, or a GitHub release run. It does not authorize any of those
operations.

## Candidate identity

| Identity | Value |
| --- | --- |
| Assessed commit | `0886c7164a8817eca100ae268b4b728a46c8c4a4` |
| Assessed Git tree | `a201697f6ec93022c72ba9f8a52eb463ace1485e` |
| Commit timestamp / subject | `2026-10-07T19:52:58-05:00` / `chore: update documentation for Codinglair TAF version 1.3.0` |
| Candidate source diff | clean apart from this REL readiness correction; the private handoff is intentionally ignored |
| Hosted Apple tested commit / tree | `ff1cf9d0ede23737966418665a9739356bb3e846` / `ad6483872bfc589e7792c7faa6bf18c87ca5ae54` |
| Difference from hosted tested commit | version/documentation records and this readiness record only; no Runtime, fixture, runner, or workflow change |

Material changes to Runtime mobile code, the Apple consumer, fixture, runner, workflow, versioned
coordinates, or packaged image reopen the affected checks. Documentation-only append of the hosted
result does not invalidate that hosted result.

## Readiness rationale

The checked-in root revision is 1.3.0 and all script-managed documentation markers match it. The
root revision remains the single Maven version authority: child POMs inherit/interpolate it, while
the repository hook propagated it to marker-bearing documents. A clean build without an override proves the
complete candidate package set, BOM/POM alignment, sources, Javadocs and SBOMs.

The passing hosted Apple qualification is immutably recorded against the pre-promotion 1.2.0
candidate artifacts and the exact tested source. REL-130-001 scope 5 requires the release owner to
rebind evidence to promoted provenance later and expressly states that this is not a dependency or
acceptance criterion. No Runtime, fixture, runner or workflow code differs between the tested
candidate and the assessed tree.

The reusable local image `codinglair-taf-mcp:mcp-130-001` supplies the required candidate MCP image
identity: OCI version 1.3.0 and digest
`sha256:b25fb863b97a456bcdc6b6c438b8e8e0f42741e3e21e6a78d0bc87653d1731b3`. Its working-tree revision
label and existing supply-chain workflow/evidence are sufficient local candidate evidence; binding
the promoted image to the final release source revision is subsequent release-owner work. The later
VER image remains useful corroborating evidence at digest
`sha256:5416c8b69704b233e2374480a6b07e6250248b03d38c638be24d6252c38c793c`.

## Readiness checks

| Area | Outcome | Evidence / disposition |
| --- | --- | --- |
| VER-130-001 dependency | PASS | Implementation handoff complete; code review PASS; architectural review PASS; local contracts, Android regression and isolated-consumer evidence are truthfully classified. |
| VER-130-002 dependency | PASS | Implementation, code review and architectural review complete. Candidate 2 hosted Apple simulator qualification PASSED; promoted-provenance rebinding is subsequent operational work. |
| FR-MOB-011, FR-MOB-013–026 / BRD 33.4 | PASS | Predecessor handoffs/reviews and candidate 2 cover the required contracts, native/hybrid/Safari behavior, lifecycle/evidence/security/isolation, Android compatibility, consumer/MCP surfaces and truthful limitations. |
| Java / Spring baseline | PASS | Clean staging compiled with Java release 25; root pins Spring Boot 4.1.0 and Spring AI 2.0.1. |
| 1.3.0 Maven packaging | PASS | `clean deploy -Prelease-staging -DskipTests` passed 51/51 projects at the checked-in root revision. |
| BOM / POM alignment | PASS | Staged parent, BOM and modules consistently resolved as 1.3.0. |
| Sources / Javadocs / SBOM / metadata | PASS | 822 staged files; 37 primary JARs; zero missing sources/Javadoc companions; license-bearing POMs and CycloneDX artifacts produced. |
| Publication-size fix / limit | PASS | No staged file exceeded the applicable 5 MiB PR publication limit. |
| Public/private boundary | PASS | Zero staged paths matched demo, proprietary, or Quality Intelligence namespaces; Sauce demo deploy was skipped. |
| External consumer conformance | PASS | 15/15 isolated projects passed against checked-in 1.3.0 artifacts, including 37 mobile direct-consumer cases and maximal starter convergence. Initial PKIX and sandbox-network failures are retained below. |
| Apple compatibility record | PASS for selected 1.2.0 tuple | Candidate 2 records exact hosted tuple and successful real simulator outcomes; physical device, iPad, provider and broader tuple limits remain explicit. |
| MCP image identity / supply chain | PASS | Candidate OCI version 1.3.0 image digest is recorded; existing SBOM, provenance, vulnerability, signing and release controls are present. Promoted-source rebinding/publication is subsequent work. |
| Changelog / migration / docs / examples | PASS | Apple guides, compatibility record, examples, architecture, security and operations documents exist. Marked versions correctly follow root revision 1.3.0. No unapproved public-contract break requiring separate migration guidance was identified. |
| API compatibility / lifecycle / concurrency | PASS by reviewed predecessor evidence | No REL change to public APIs or behavior. VER reviews cover typed named controllers, TestSession cleanup, isolation, cancellation and controlled-failure cleanup. |

## Local artifact checksums

These files were produced from the assessed tree at its checked-in 1.3.0 revision and fixed output
timestamp `2026-10-07T00:00:00Z`. They are local readiness evidence; the release owner later binds promoted
artifacts to the promoted tree and records their final digests.

| Artifact | SHA-256 |
| --- | --- |
| `codinglair-taf-bom-1.3.0.pom` | `c3f52a0582b25b7074aaad99c5cb6f7d0804c43bf67cd6ba51645a0c85479ab1` |
| `taf-mobile-appium-1.3.0.jar` | `6552d7195589d114534cd34735743bf71b97838b8c48efc1e02a2486010c911f` |
| `taf-mobile-appium-1.3.0-sources.jar` | `f79a9bc246784a99d7b775643b179d52160050f8f8e47043d5021dcb76562363` |
| `taf-mobile-appium-1.3.0-javadoc.jar` | `bd15179c427cf016840603e00fb4e6b50a3eda6383987824e821904d3094350d` |
| `taf-mcp-transport-http-1.3.0.jar` | `5ab4b55f4189f15f847705fbb6e5c8560e1af985983e2f6a1a9c5558975fd065` |

Hosted evidence archive `apple-simulator-qualification-evidence`, artifact ID `11515947412`, is
recorded with SHA-256 `12c90dff4bfa9f45ea61dc93cadcd96bd30c82d5abdb846df14f47799900ba65`
and scheduled expiry `2026-10-21T22:35:06Z`. Preserve or rebind required evidence before expiry.

## Commands and outcomes

| Command / check | Outcome |
| --- | --- |
| `git status --porcelain=v2`; `git rev-parse HEAD`; tree/diff fingerprint checks | PASS; initial tree clean at the identity above |
| `java build-support/scripts/SyncDocVersion.java --check` | PASS; all marked documents match authoritative root revision 1.3.0 |
| `mvnw.cmd ... -Drevision=1.3.0` without quoting the property | COMMAND ERROR before build: PowerShell split `.3.0`; no product result inferred |
| `.\mvnw.cmd -B -ntp clean deploy -Prelease-staging -DskipTests "-Drevision=1.3.0" "-Dproject.build.outputTimestamp=2026-10-07T00:00:00Z"` | PASS; 51/51 reactor projects |
| `.\mvnw.cmd -B -ntp clean deploy -Prelease-staging -DskipTests "-Dproject.build.outputTimestamp=2026-10-07T00:00:00Z"` after the root revision transition | PASS; 51/51 reactor projects at checked-in 1.3.0 |
| staged repository companion, size and boundary inspection | PASS; 822 files, 37 primary JARs, 0 missing companions, 0 files over 5 MiB, 0 private/demo matches |
| `.\mvnw.cmd -B -ntp -N -Pconsumer-smoke verify "-Drevision=1.3.0"` | ENVIRONMENTAL FAILURE: 15/15 stopped at PKIX before model resolution |
| same command with Windows trust store in sandbox | ENVIRONMENTAL FAILURE: sandbox denied network access |
| same command with Windows trust store and approved network access | PASS; 15/15 isolated consumers, no failures/errors/skips |
| `.\mvnw.cmd -B -ntp -N -Pconsumer-smoke verify` with Windows trust store and approved network access after the root revision transition | PASS; 15/15 isolated consumers at checked-in 1.3.0 |
| local Docker image inventory and inspect | PASS; establishes the candidate 1.3.0 digest and corroborating later 1.2.0 image identity above |
| `git diff --check` before documentation edits | PASS |

Tests were not rerun in the staging build because predecessor Gate E test evidence and reviews are
already complete and this assignment permits a limited evidence recheck. The consumer gate executed
its own tests. No production behavior changed in REL-130-001.

## Accepted limitations

- Physical Apple devices, iPad, device-cloud providers, remote customer hosts, other Xcode/iOS
  tuples and exhaustive concurrency remain **UNVERIFIED**, not failed or certified. BRD 33.4 and
  ADR-035 do not require a paid provider, a physical device, or an exhaustive matrix.
- The qualified selected boundary is iPhone 16 / iOS 18.5 simulator, Xcode 16.4, Appium 3.0.0,
  XCUITest 10.0.0, Java client 10.1.1 and Selenium 4.43.0. Generic configurability is not provider
  certification.
- Conditional device logs and video were attempted; exact availability remains in the hosted
  archive. Required screenshot and page-source evidence passed.
- No public Maven/image availability, final master SHA, tag, merge beyond the assessed candidate,
  GitHub release run, or publication signature is claimed or required for this readiness document.

## Release-owner follow-up

1. Bind the promoted Maven artifacts, Apple evidence and MCP
   image to the promoted tree/artifact provenance with final hashes.
2. Perform separately authorized merge/tag/sign/publish/promotion operations and retain the
   resulting supply-chain evidence.
3. Reopen affected verification/readiness only if those operations introduce a material candidate
   change or a gate fails. Routine provenance rebinding is not a REL-130-001 acceptance dependency.

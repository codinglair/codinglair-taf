# Codinglair TAF 1.3.0 release readiness

**Assessment date:** 2026-10-07

**Assignment:** REL-130-001

**Verdict:** **NOT READY**

**Promotion status:** **BLOCKED** until every blocker below is closed against one identified
candidate tree and artifact set

This is a pre-publication readiness decision. It does not require a merge, tag, final master SHA,
public Maven artifacts, a public image, or a GitHub release run. It does not authorize any of those
operations.

## Candidate identity

| Identity | Value |
| --- | --- |
| Assessed commit | `1bec0002baff0026957e3d009232776c17cfc94d` |
| Assessed Git tree | `cbd9609263bef52d7a06f15cd5344a5edd15bb6e` |
| Commit timestamp / subject | `2026-10-07T18:32:03-05:00` / `Merge pull request #91 from codinglair/feature/VER-130-002` |
| Initial tracked diff | clean; empty patch SHA-1 `e69de29bb2d1d6434b8b29ae775ad8c2e48c5391` |
| Hosted Apple tested commit / tree | `ff1cf9d0ede23737966418665a9739356bb3e846` / `ad6483872bfc589e7792c7faa6bf18c87ca5ae54` |
| Difference from hosted tested commit | this tree adds only `docs/qualification/apple/VER-130-002-candidate-2.md`; no Runtime, fixture, runner, or workflow change |

Material changes to Runtime mobile code, the Apple consumer, fixture, runner, workflow, versioned
coordinates, or packaged image reopen the affected checks. Documentation-only append of the hosted
result does not invalidate that hosted result.

## Release blockers

1. **The checked-in candidate is not versioned 1.3.0.** The root `pom.xml` authoritative
   `<revision>` is `1.2.0`. `java build-support/scripts/SyncDocVersion.java --check` passes only
   because every `taf-version` marker is consistently synchronized to that 1.2.0
   revision. Release ownership must change the root revision to 1.3.0, allow the repository hook or
   synchronization script to update marked Markdown, review/stage those changes, and produce a new
   candidate identity.
2. **The passing Apple qualification is bound to 1.2.0 artifacts.** Candidate record
   `VER-130-002-candidate-2.md` reports a successful native, hybrid, and Safari hosted simulator run,
   but its qualified tuple explicitly identifies `1.2.0` TAF artifacts. After the versioned
   candidate is produced, the release owner must rebind or rerun the required evidence against the
   1.3.0 artifact provenance. No equivalence is inferred merely from a command-line version
   override.
3. **No current-source-bound 1.3.0 MCP release image exists locally.** The reusable local image
   `codinglair-taf-mcp:mcp-130-001` has OCI version 1.3.0 and digest
   `sha256:b25fb863b97a456bcdc6b6c438b8e8e0f42741e3e21e6a78d0bc87653d1731b3`, but its revision label is
   `working-tree-mcp-130-001`, so it is not bound to this candidate. The later VER image is bound to
   commit `84140ae9a68582a06bbe1f844ba51aa86e7cd0e6`, but is versioned 1.2.0 with digest
   `sha256:5416c8b69704b233e2374480a6b07e6250248b03d38c638be24d6252c38c793c`.
   Build and verify the MCP image from the versioned candidate and record its immutable local image
   ID or registry digest and source revision. Registry publication remains subsequent work.

## Readiness checks

| Area | Outcome | Evidence / disposition |
| --- | --- | --- |
| VER-130-001 dependency | PASS | Implementation handoff complete; code review PASS; architectural review PASS; local contracts, Android regression and isolated-consumer evidence are truthfully classified. |
| VER-130-002 dependency | PASS with release rebinding blocker | Implementation, code review and architectural review complete. Candidate 2 hosted Apple simulator qualification PASSED; release coordinate is 1.2.0 and must be rebound as described above. |
| FR-MOB-011, FR-MOB-013–026 / BRD 33.4 | PASS for implemented capability; release identity blocked | Predecessor handoffs/reviews and candidate 2 cover the required contracts, native/hybrid/Safari behavior, lifecycle/evidence/security/isolation, Android compatibility, consumer/MCP surfaces and truthful limitations. |
| Java / Spring baseline | PASS | Override staging compiled with Java release 25; root pins Spring Boot 4.1.0 and Spring AI 2.0.1. |
| 1.3.0 Maven packaging mechanics | PASS, non-authoritative override | `clean deploy -Prelease-staging -DskipTests -Drevision=1.3.0` passed 51/51 projects. This validates mechanics only and does not replace the checked-in revision. |
| BOM / POM alignment | PASS in override staging; checked-in version BLOCKED | Staged parent, BOM and modules resolved as 1.3.0. Root source remains 1.2.0. |
| Sources / Javadocs / SBOM / metadata | PASS in override staging | 822 staged files; 37 primary JARs; zero missing sources/Javadoc companions; license-bearing POMs and CycloneDX artifacts produced. |
| Publication-size fix / limit | PASS | No staged file exceeded the applicable 5 MiB PR publication limit. |
| Public/private boundary | PASS | Zero staged paths matched demo, proprietary, or Quality Intelligence namespaces; Sauce demo deploy was skipped. |
| External consumer conformance | PASS in override staging | 15/15 isolated projects passed, including 37 mobile direct-consumer cases and maximal starter convergence. Initial PKIX and sandbox-network failures are retained below. |
| Apple compatibility record | PASS for selected 1.2.0 tuple | Candidate 2 records exact hosted tuple and successful real simulator outcomes; physical device, iPad, provider and broader tuple limits remain explicit. |
| MCP image identity / supply chain | BLOCKED | Existing image controls, SBOM/provenance/release workflow and prior local image IDs exist, but no image combines current source revision with release version 1.3.0. |
| Changelog / migration / docs / examples | BLOCKED only by version transition | Apple guides, compatibility record, examples, architecture, security and operations documents exist. Marked versions intentionally follow root revision and have not yet transitioned to 1.3.0. No unapproved public-contract break requiring separate migration guidance was identified. |
| API compatibility / lifecycle / concurrency | PASS by reviewed predecessor evidence | No REL change to public APIs or behavior. VER reviews cover typed named controllers, TestSession cleanup, isolation, cancellation and controlled-failure cleanup. |

## Local artifact checksums

These files were produced from the assessed tree with a non-authoritative `-Drevision=1.3.0`
override and fixed output timestamp `2026-10-07T00:00:00Z`. They demonstrate packaging readiness;
they are not promotable release artifacts while blocker 1 remains open.

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
| `java build-support/scripts/SyncDocVersion.java --check` | PASS for authoritative root revision 1.2.0; confirms blocker rather than 1.3.0 readiness |
| `mvnw.cmd ... -Drevision=1.3.0` without quoting the property | COMMAND ERROR before build: PowerShell split `.3.0`; no product result inferred |
| `.\mvnw.cmd -B -ntp clean deploy -Prelease-staging -DskipTests "-Drevision=1.3.0" "-Dproject.build.outputTimestamp=2026-10-07T00:00:00Z"` | PASS; 51/51 reactor projects |
| staged repository companion, size and boundary inspection | PASS; 822 files, 37 primary JARs, 0 missing companions, 0 files over 5 MiB, 0 private/demo matches |
| `.\mvnw.cmd -B -ntp -N -Pconsumer-smoke verify "-Drevision=1.3.0"` | ENVIRONMENTAL FAILURE: 15/15 stopped at PKIX before model resolution |
| same command with Windows trust store in sandbox | ENVIRONMENTAL FAILURE: sandbox denied network access |
| same command with Windows trust store and approved network access | PASS; 15/15 isolated consumers, no failures/errors/skips |
| local Docker image inventory and inspect | PASS as an inspection; establishes the two non-promotable image identities above |
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

1. Change the authoritative root revision to 1.3.0 and review/stage all hook-managed documentation
   changes; create a new candidate commit/tree identity.
2. Re-run version synchronization check, release staging, isolated consumers and applicable
   compatibility/security/license gates against that exact tree without a revision override.
3. Rebind or rerun the Apple qualification so its retained manifest and artifact hashes identify
   the promoted 1.3.0 artifacts; append a new immutable qualification record.
4. Build the MCP image from that exact source identity, verify both profiles and supply-chain
   evidence, and record its immutable digest.
5. Reopen REL-130-001 for a limited evidence recheck. If READY, a human release owner may then
   perform separately authorized merge/tag/sign/publish/promotion operations and bind public
   provenance to the promoted tree and artifact digests.

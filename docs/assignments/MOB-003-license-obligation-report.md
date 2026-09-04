# MOB-003 License and Obligation Report

## Executive decision summary

The governed deliverable is a reference qualification recipe and gate. Its
ephemeral local/CI image is a framework-development health checkpoint;
consumers provide their own mobile infrastructure. No image publication or
consumer delivery is permitted. DEVOPS-001R integrates the qualification
recipe and gate—not a distributable image digest.

The 256 technical inventory records do **not** require 256 independent Product
Owner decisions. They collapse into routine obligation families plus three
Google SDK exceptions.

Product Owner governance approval is requested only for these decisions:

1. Accept the reference qualification recipe and ephemeral local/CI execution
   model: the repository contains checksum-pinned instructions, while authorized
   local/CI builders download Google artifacts directly after explicit
   SDK-license acceptance. The generated image is not pushed, published,
   transferred, attached, adopted, or delivered to consumers.
2. Accept the controls for the three Google artifacts: emulator, platform
   tools, and the Google APIs system image. Preserve their notices, require
   explicit SDK acceptance, and prohibit image distribution without a new
   review.
3. Accept the summarized open-source compliance controls below for unmodified
   Ubuntu and Android guest components. If the image is ever conveyed outside
   the approved execution boundary, complete the applicable notice and
   corresponding-source obligations before distribution.

No approval of undifferentiated package rows is requested.

This is a governance decision about the permitted operating model, accepted
compliance controls, and whether SEC-003 may treat the summarized records as
`ALLOWED`. It is not a legal opinion. Do not describe it as legal approval
unless identified counsel or another authorized legal reviewer actually reviews
the specified terms, evidence, distribution model, and jurisdiction, and that
review is recorded by name/role, scope, date, and decision reference.

## Why the inventory changed from 254 to 256

The former count was mechanically complete but conceptually wrong:

```text
old = 1 project-authored record + all 253 SPDX package records = 254
```

That count included the SPDX document root as though the image aggregate were
another licensed component, while omitting explicit contractual records for the
three downloaded Google artifacts. The corrected count is:

```text
new = 1 project-authored record
    + 252 actual SPDX components (253 packages minus the synthetic root)
    + 3 explicit Google records (emulator, platform tools, system image)
    = 256
```

The net change is `-1 + 3 = +2`. No runtime component was added, removed, or
changed; this is a correction to evidence modeling.

## Qualification model and material obligations

The current model distributes the Apache-2.0 repository source and reference
qualification recipe, not the generated Android image. Each authorized builder
obtains the SDK artifacts from Google and accepts the SDK terms. The ephemeral
image is only a framework-development health checkpoint. Consumers provide
their own mobile infrastructure through the existing Appium contract. This
materially limits the current obligations:

- Repository-authored files: Apache-2.0; retain the repository license and notices.
- Permissive dependencies: retain copyright and license notices.
- GPL components: if the image is conveyed, provide applicable complete
  corresponding source and license information.
- LGPL/MPL components: preserve notices and, if conveyed, applicable
  source/modification and relinking rights.
- Documentation/data licenses: preserve attribution, license text, and stated
  share-alike requirements when relevant material is conveyed.
- Google SDK components: explicit SDK acceptance, intact proprietary-rights and
  third-party notices, authorized build/use only, and no image publication.
  See the [Android SDK terms](https://developer.android.com/studio/terms) and
  [Android legal notice](https://developer.android.com/legal).

Image publication or consumer delivery and production adoption remain outside
this approval request. After approval, DEVOPS-001R may integrate the reference
qualification recipe and gate—not a distributable image digest.

## Ephemeral CI use and prohibited transfer

Ephemeral CI use requires all of the following:

- one authorized workflow run builds from pinned inputs after explicit SDK
  acceptance;
- the image remains only in that run's runner-local Docker content store;
- containers and project networks are unconditionally removed;
- the hosted runner is destroyed after the run, or an equivalent isolated
  worker deletes its image/layers before reuse;
- only non-image evidence is uploaded: logs, reports, SBOM, provenance, scan
  results, hashes, notices, and build metadata;
- no image or layer cache containing Google SDK payloads is exported or shared.

Prohibited transfer/publication includes `docker push`, registry or OCI export,
`docker save`, image tarballs, uploaded image/layer artifacts, shared or remote
BuildKit caches containing SDK payloads, release attachments, copying/loading
the image into another host or shared daemon, or making it available through an
internal or public registry without a separate distribution review.

A persistent self-hosted runner, shared Docker daemon, reusable image cache, or
CI artifact containing the image is not ephemeral merely because containers are
stopped. The prepared workflow uploads evidence only and does not transfer the
image.

## Summarized technical inventory

| Family | Records | Required control |
|---|---:|---|
| Permissive or notice-only | 81 | Preserve copyright, attribution, and license notices |
| Strong copyleft or mixed expressions containing GPL | 168 | Preserve licenses; if conveyed, provide applicable corresponding source and offers |
| Weak copyleft without GPL in the expression | 4 | Preserve notices and applicable source/modification/relinking rights if conveyed |
| Google SDK contractual exceptions | 3 | Explicit acceptance, preserve notices, authorized local/CI use only, no image publication |
| Remaining `NOASSERTION` | **0** | None |

Counts are conservative: a mixed expression is placed in the stronger family
even when it contains a permissive alternative. Exact expressions, versions,
evidence paths, and mechanical dispositions remain in
`target/mob-003/candidate-a-license-inventory.csv`.

## Reproducibility of the former `NOASSERTION` resolutions

`complete-evidence.ps1` performs this fail-closed proof on every run:

1. Parse the exact SPDX SBOM whose SHA-256 is in the evidence manifest.
2. Require exactly 38 original `licenseDeclared=NOASSERTION` records.
3. Identify the synthetic root by fixed SPDX ID and container purpose.
4. Require every other original name/version to have an explicit resolution,
   obligation, and evidence locator; any unmapped record aborts generation.
5. Emit all 38 original SPDX IDs and resolutions to
   `candidate-a-noassertion-resolutions.csv`.
6. Require exactly one removed synthetic aggregate, 37 mapped records, and zero
   `NOASSERTION` values in the corrected 256-row inventory.
7. SHA-256 hash the resolution CSV with the rest of the evidence package.

Ubuntu locators point to copyright files inside the exact image digest; guest
mappings point to preserved notices and authoritative upstream sources. A future
SBOM/name/version change therefore fails instead of inheriting a stale mapping.

## Resolution of the original 38 `NOASSERTION` records

### Synthetic aggregate removed — 1 record

The SPDX document-root package `codinglair-taf/android-emulator` is an image
aggregate, not another independently licensed component. It is excluded from
component decisions. Its contents remain represented by project, Google,
Ubuntu, and Android guest records.

### Ubuntu records resolved from exact-image copyright evidence — 32 records

| Source/license family | Records | Resolved expression | Principal obligation |
|---|---:|---|---|
| `base-files` | 1 | GPL-2.0-or-later | License and corresponding source if conveyed |
| GCC 14 runtime | 1 | GPL-3.0-or-later with GCC Runtime Library Exception, LGPL-2.1-or-later, and documented permissive components | Preserve notices; source duties for covered parts if conveyed; runtime exception applies |
| libxcrypt | 2 | LGPL-2.1-or-later plus documented permissive files | LGPL source/relinking rights and notices if conveyed |
| libdrm | 5 | MIT | Preserve copyright and license notice |
| libpciaccess | 2 | MIT upstream plus GPL-2.0-or-later Debian packaging | Preserve notices; packaging source if conveyed |
| libXau | 2 | MIT/X11 family | Preserve copyright and license notice |
| libxcb | 9 | MIT/X11 family | Preserve copyright and license notice |
| libXdmcp | 2 | MIT/X11 family | Preserve copyright and license notice |
| libXext | 2 | MIT/X11 family | Preserve copyright and license notice |
| libXi | 2 | MIT/X11 family | Preserve copyright and license notice |
| libxkbfile | 2 | MIT/X11 family | Preserve copyright and license notice |
| libzstd | 1 | BSD-3-Clause or GPL-2.0-only, plus Zlib and MIT/Expat bundled files | Preserve notices; source if GPL option is used and conveyed |
| ubuntu-keyring | 1 | GPL-2.0-or-later | Preserve license and corresponding source if conveyed |

Evidence comes from the exact candidate's `/usr/share/doc/*/copyright` files.
Several SPDX rows are binary/source aliases of one upstream copyright file; the
table intentionally reviews those once.

### Android guest records resolved from authoritative upstream identity — 5 records

| Guest component | Resolved expression | Evidence and obligation |
|---|---|---|
| Bash 5.2.21 | GPL-3.0-or-later | GNU Bash distribution; preserve GPL and provide corresponding source if conveyed |
| Gzip 1.12 | GPL-3.0-or-later | [GNU Gzip licensing](https://www.gnu.org/software/gzip/); preserve GPL and provide corresponding source if conveyed |
| Linux kernel 6.1 Android branch | GPL-2.0-only | [Linux kernel licensing rules](https://kernel.org/doc/html/next/process/license-rules.html); complete corresponding source if conveyed |
| OpenSSL 3.0.13 | Apache-2.0 | [OpenSSL licensing](https://openssl-library.org/source/license/); preserve license and notices |
| util-linux 2.39.3 | Mixed GPL-2.0-or-later, LGPL-2.1-or-later, BSD, MIT, and public-domain files | Preserve file-level notices and applicable GPL/LGPL source and relinking rights if conveyed |

The preserved system-image notice remains authoritative for the exact binary
bundle. Upstream mappings replace scanner `NOASSERTION`; they do not claim
that every file in a mixed project has one uniform license.

## Genuine unresolved items

There are no remaining unresolved `NOASSERTION` package identities.

The genuinely unresolved governance issues are contractual and qualification-
boundary specific:

- Whether the direct-download, explicit-acceptance, no-image-publication model
  is acceptable under the Android SDK agreement.
- Whether the three Google artifact records may receive SEC-003 `ALLOWED`
  dispositions under that model.
- Whether a proposed execution environment meets the strict ephemeral
  definition above. Internal image/cache movement is prohibited by the current
  model rather than left ambiguous.

These require one Product Owner governance approval of this summarized model
and controls, not package-by-package approval. Escalate for actual legal review
if governance cannot determine whether the Google terms permit the model or if
any image transfer/distribution is proposed.

## Approval application

After this report is approved, its exact SHA-256 and the Product Owner decision
reference are supplied to `complete-evidence.ps1`. The script maps that single
report approval to all evidence rows, records the reference against the exact
image digest, and requires SEC-003 JSON `result=PASS`.

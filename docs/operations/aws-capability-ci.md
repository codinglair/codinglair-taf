# AWS capability local and CI verification

INF-110-001 provides one set of Maven-backed gates for Java 25 workstations and clean GitHub
runners. Docker is needed only for `localstack` and `consumer-smoke`. Testcontainers maps a random
host port, so no port forwarding, fixed port, persistent LocalStack volume, or hosts-file entry is
required. Maven dependency caching is allowed; container state is never cached.

Use the [AWS messaging guide](../reference/aws-messaging.md) for application configuration,
controller examples, standalone execution, emulator limitations, and the authorized-AWS
qualification procedure.

LocalStack clients use the non-secret placeholder credential pair `localstack` / `localstack`,
which LocalStack requires only for request signing. These values are not authorized AWS
credentials and must never be reused for an AWS endpoint. Live AWS authentication is isolated to
the protected OIDC flow described below; static AWS access keys are neither accepted nor required.

## Local commands

Run a gate from the repository root. Each command returns Maven's failure status and can be rerun
without a separate LocalStack start or stop operation.

| Gate | Windows PowerShell | POSIX shell |
| --- | --- | --- |
| `unit-contract` | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File build-support/scripts/aws-ci.ps1 unit-contract` | `bash build-support/scripts/aws-ci.sh unit-contract` |
| `localstack` | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File build-support/scripts/aws-ci.ps1 localstack` | `bash build-support/scripts/aws-ci.sh localstack` |
| `mcp-contract-leak` | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File build-support/scripts/aws-ci.ps1 mcp-contract-leak` | `bash build-support/scripts/aws-ci.sh mcp-contract-leak` |
| `consumer-smoke` | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File build-support/scripts/aws-ci.ps1 consumer-smoke` | `bash build-support/scripts/aws-ci.sh consumer-smoke` |
| `full-reactor` | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File build-support/scripts/aws-ci.ps1 full-reactor` | `bash build-support/scripts/aws-ci.sh full-reactor` |

The consumer command deliberately stages version `1.1.0` into
`target/staging-repository` before launching Maven Invoker. Invoker uses its own repository and the
staged coordinates, not artifacts installed by another job or a hidden reactor relationship.

## CI tiers and evidence

`.github/workflows/aws-capability.yml` is the authoritative reusable workflow. Pull requests run it
only for the AWS module, AWS consumer, AWS/INF assignment contract, root build, or workflow/script
changes. Nightly and release matrices run all gates. Each job checks out a clean tree and uses only
the safe Maven dependency cache; LocalStack mutable state is never restored or saved.

The LocalStack job builds a bounded, text-only failure bundle. `ArtifactSanitizer` redacts common
authorization, credential-reference, AWS credential-variable, bearer, receipt, token, and canary
forms. `ArtifactLeakCheck` then fails closed before upload when prohibited material or oversized
files remain. Raw container inspection, environment dumps, and LocalStack state are not uploaded.

The supply-chain job separates scanner execution from findings disposition. Trivy records high and
critical vulnerability and embedded-secret findings in `localstack-trivy.json`, and records all
license severities in `localstack-licenses.json`. Findings do not directly set the action exit code:
the upstream emulator includes operating-system advisories, example AWS identifiers, emulator CA
keys, and GPL/LGPL system packages whose relevance cannot be decided from Trivy severity alone.
Scanner execution, evidence generation, sanitization, and leak-check errors still fail the job.

Every image or digest change requires review of the retained reports before approval. The image is
an unmodified, ephemeral CI test appliance: it is not published, embedded in a TAF artifact, exposed
outside the isolated runner, or used for production. Release distribution remains subject to the
repository's stricter release vulnerability and license policies.

The supply-chain lane pulls the pinned linux/amd64 digest, scans vulnerabilities, secrets, and
licenses with Trivy, produces a CycloneDX SBOM, and retains the resolved repository digest. The
Maven full-reactor gate also runs dependency, architecture, API, and schema compatibility profiles.
The dependency versions, licenses, compatibility rationale, and image digest are recorded in
`docs/architecture/decisions/AWS-110-001-dependency-compatibility.md`.

## Authorized AWS boundary

Live AWS is not required for ordinary local, PR, nightly, or LocalStack qualification. The
`authorized-aws` workflow input is false by default and enters the protected
`authorized-aws-qualification` GitHub environment only after an explicit dispatch and environment
approval. That boundary-only job grants `contents: read`, requires a configured role reference,
and neither requests an OIDC token nor accepts static access-key secrets. It does not authenticate
or claim that live qualification ran. OIDC permission and token exchange belong in the future,
separately approved live qualification workflow rather than in reusable PR/nightly CI. The live
qualification command remains release-controlled until that suite and environment exist;
LocalStack results must not be reported as authorized-AWS evidence.

## Failure triage

- A missing Docker daemon affects only container-backed gates; unit, MCP, and full-reactor gates
  remain runnable.
- A consumer preflight failure identifies the missing staged BOM and is corrected by rerunning the
  single `consumer-smoke` entry point from the repository root.
- A supply-chain finding blocks the lane. Do not suppress it; record its disposition through the
  dependency/image approval process.
- Testcontainers cleanup is session/provider owned and idempotent. A residual resource or leak
  assertion is a gate failure, not a reason to upload raw Docker state.

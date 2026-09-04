# Container images and supply-chain controls

The repository defines two Linux/amd64 Java 25 images in `docker-bake.hcl`:

- `control-plane` packages the OIDC-secured Streamable HTTP MCP application on the minimal
  Eclipse Temurin Java 25 JRE image and runs as UID/GID 10001.
- `worker` packages the isolated execution-worker boundary on the Java 25 JDK image and runs as
  UID/GID 10002. The JDK is intentional because governed build/test jobs compile Java. A worker
  starts idle when no explicit job command is supplied; orchestration must supply an allowlisted
  command and mount only its scoped workspace/artifact locations.

Both Eclipse Temurin bases are pinned by manifest digest in their Dockerfiles. Updating either
digest is a supply-chain policy change: resolve the named Java 25 tag, review upstream release and
license information, rebuild both images, generate fresh SBOM/scan evidence, and rerun the smoke
gate. Build arguments contain only version/revision/timestamp metadata; credentials and secret
values are forbidden.

## Local verification

```text
docker buildx bake --check
docker buildx bake --load
./mvnw -Pcontainer-images verify
VERSION="$(sed -n 's:.*<revision>\([^<]*\)</revision>.*:\1:p' pom.xml | head -n 1)"
bash containers/smoke.sh worker "codinglair/taf-worker:$VERSION"
bash containers/smoke.sh control-plane "codinglair/taf-control-plane:$VERSION"
```

## CI versus local verification

| Verification path | Purpose | Evidence authority |
|---|---|---|
| `.github/workflows/container-images.yml` on `ubuntu-24.04` | Clean-checkout loadable build, retained SPDX/Trivy reports, Critical-CVE gate, and restricted image smokes | **Authoritative compliance and retention source.** A required CI job must pass for the reviewed revision; local success does not substitute for it. |
| The commands above on Docker Desktop's Linux engine | Fast developer validation of the same Bake targets, image startup, health checks, and runtime restrictions before review | Supporting diagnostic evidence only. It neither proves the GitHub-hosted environment nor retains release evidence. |

The DEVOPS-003 implementation commands were locally verified from the prepared working tree on
Docker Desktop's Linux engine. They were not a clean-room GitHub Actions execution. The workflow
must run from a fresh checkout on Ubuntu and retain its per-image artifacts before its results are
used as CI or release compliance evidence. If local and CI results differ, treat the CI failure as
authoritative and investigate the environment or image difference; never waive the CI gate based
only on a local pass.

The smoke gate applies a read-only root filesystem, all-capability drop, `no-new-privileges`, PID,
CPU, memory, and writable-tmpfs bounds. Worker networking is disabled and its workspace/artifact
tmpfs mounts are private to UID 10002. The control-plane packaging smoke disables its remote MCP
binding because it deliberately has no identity provider; MCP-009's Keycloak integration gate is
the authoritative real-OIDC test.

## CI evidence and release use

`.github/workflows/container-images.yml` builds without pushing, retains an SPDX JSON SBOM and
Trivy JSON vulnerability report for 30 days, prints actionable package/CVE/fixed-version
diagnostics before rejecting every detected Critical vulnerability, and runs the restriction
smoke. No vulnerability waiver is
encoded by the workflow. Any approved exception must identify the exact image digest,
CVE, reachability analysis, owner, and expiry; it must never be a severity-wide ignore.

The workflow's provenance and OCI revision/version/created/source/license labels are
signing-ready metadata. This workflow neither requests registry write permission nor publishes or
signs an image.

The images contain no secret. Runtime configuration, including issuer and audience, must be
provided by deployment configuration. Never pass tokens, passwords, or resolved secret values as
Docker build arguments, OCI labels, or command-line values.

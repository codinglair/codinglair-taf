# Container images and supply-chain controls

## Official MCP image

`codinglair/codinglair-taf-mcp:1.2.0` is the official Java 25 MCP image. One immutable image
contains both mutually exclusive runtime profiles. Invoke it with `stdio` for a client-owned,
interactive pipe (never a TTY), or `streamable-http` for detached/container-orchestrated use:

```text
docker run --rm -i --read-only \
  --mount type=bind,src=<approved-workspace>,dst=/workspace \
  codinglair/codinglair-taf-mcp:1.2.0 stdio

docker run --detach --read-only --publish 127.0.0.1:8080:8080 \
  --mount type=bind,src=<config>,dst=/etc/taf-mcp,readonly \
  --mount type=volume,src=taf-mcp-state,dst=/var/lib/taf-mcp \
  codinglair/codinglair-taf-mcp:1.2.0 streamable-http
```

Configuration, OIDC settings, policy, workspace, state, and secret-provider references are
external inputs. Never put resolved credentials in environment variables, command-line options,
image labels, or build arguments. The complete setting precedence and mount contract is in the
[MCP image runtime contract](../reference/mcp-image-runtime-contract.md). STDIO health is an MCP
initialize/ping exchange. HTTP exposes `/actuator/health/liveness` and
`/actuator/health/readiness` on the configured port.

Build and qualify the same Dockerfile used by CI on the host architecture:

```text
docker buildx build --load --file containers/mcp/Dockerfile \
  --build-arg VERSION=1.2.0 --build-arg REVISION=<git-sha> \
  --build-arg CREATED=<rfc3339-timestamp> \
  --tag codinglair/codinglair-taf-mcp:1.2.0 .
bash containers/mcp/smoke.sh codinglair/codinglair-taf-mcp:1.2.0
docker history codinglair/codinglair-taf-mcp:1.2.0
docker image inspect codinglair/codinglair-taf-mcp:1.2.0
```

`.github/workflows/container-images.yml` performs the loadable PR build, both-profile smoke,
SPDX SBOM generation, and fail-closed Critical vulnerability scan. The protected
`.github/workflows/mcp-image-release.yml` release job builds one `linux/amd64` + `linux/arm64`
manifest with BuildKit SBOM/provenance attestations, pushes the immutable `1.2.0` tag, optionally
adds `latest` in the same push, verifies both tags resolve to the returned digest, signs that
digest with GitHub OIDC keyless signing, and retains digest/manifest/signature evidence for 90
days. It also generates a standalone SPDX JSON SBOM, scans the published immutable digest with
Trivy, records scanner/database metadata, and rejects every unapproved Critical finding while
retaining the diagnostic evidence. It requires the protected `dockerhub-release` environment and
`DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` secrets. Reproducible tests and manifests must use
`1.2.0@sha256:<digest>`, never `latest`.

## Runtime support images

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

# Functional Kind reference deployment

This reference deploys the TAF MCP HTTP control plane, isolated worker, a local Keycloak identity
provider, and either internal or external MongoDB configuration to a fresh Kind cluster. It is a
functional developer/CI baseline, not a production topology. Ingress, TLS, HA, autoscaling,
distributed admission state, backup automation, production OIDC integration, and production SLO
claims remain deliberately out of scope.

## Prerequisites

- Docker Desktop with the Linux container engine, at least 6 GB available memory, and file sharing
  enabled for this checkout on Windows.
- Kind 0.30.0, `kubectl`, OpenSSL, and Bash. Windows users need Git for Windows Bash; the `.ps1`
  launchers invoke `bash` and otherwise use the same authoritative scripts as Linux/macOS.
- Java 25 only for the Maven structural/reactor gate. The container builds use their pinned Java 25
  builder images.
- Host port 8080 available. The cluster maps it to the control-plane NodePort.

Docker Desktop Kubernetes does not need to be enabled. Use the Docker Desktop Linux context and
confirm `docker info`, `kind version`, and `kubectl version --client` before starting.

## Fresh internal-Mongo deployment

From the repository root:

```bash
./mvnw -Pkind verify
bash deploy/kind/bootstrap.sh
bash deploy/kind/smoke.sh
bash deploy/kind/teardown.sh
```

On Windows PowerShell use `./mvnw.cmd -Pkind verify`, then the matching `bootstrap.ps1`,
`smoke.ps1`, and `teardown.ps1` launchers.

Bootstrap creates random local-only Keycloak, smoke-user, and Mongo credentials in a private
temporary directory, creates Kubernetes Secrets directly, and deletes the temporary directory on
exit. It never renders or stores a Secret manifest. Kustomize output contains only `secretKeyRef`
references. The smoke performs authenticated MCP initialize, prompt discovery, and prompt
execution; restarts the control-plane pod; and, in internal mode, proves a Mongo record survives a
Mongo pod replacement through its PVC. Teardown is idempotent and removes the entire Kind cluster,
including Secrets and persistence.

The deployment ConfigMap explicitly enables `taf.mcp.kind-reference.enabled`. This opt-in binds
the standard prompt catalog to an empty report repository and a default-deny policy that permits
only the smoke JWT's `taf.prompts.read` scope in project `kind-smoke` and environment `kind`.
The composition is disabled outside this disposable reference deployment and backs off when an
application supplies its own prompt service.

To reuse images already loaded in Docker, set `TAF_KIND_SKIP_IMAGE_BUILD=true`. Set
`TAF_IMAGE_VERSION` when using a locally built non-default tag. The bootstrap still loads both
images into Kind and waits for every rollout with bounded timeouts.

## External Mongo mode

Supply the connection URI through the process environment, never a tracked file or command-line
argument:

```bash
export TAF_KIND_MONGODB_MODE=external
export TAF_EXTERNAL_MONGODB_URI='mongodb://<resolved-at-runtime>'
bash deploy/kind/bootstrap.sh
bash deploy/kind/smoke.sh
```

External mode removes the Mongo Service and StatefulSet from rendered Kustomize output. Bootstrap
places the URI directly into the `taf-mongodb-connection` Kubernetes Secret and the control plane
receives it through `secretKeyRef`. The external database is never created, restarted, mutated, or
deleted by these scripts. Unset the environment value after deployment and use teardown normally.

## Verification and troubleshooting

The structural gate is dependency-free and can be run independently:

```bash
mkdir -p target/ci-support
javac -d target/ci-support build-support/ci/KindDeploymentContractTest.java
java -cp target/ci-support KindDeploymentContractTest
kubectl kustomize deploy/kind/overlays/internal-mongodb
kubectl kustomize deploy/kind/overlays/external-mongodb
```

`.github/workflows/kind-reference.yml` is the authoritative clean-checkout Ubuntu fresh-cluster
gate. Local Docker Desktop results are supporting developer evidence. On failure, inspect only
resource status/events and sanitized pod descriptions; do not print Secret objects, environment
variables, token responses, or raw application configuration. `bash deploy/kind/teardown.sh` is
safe after partial bootstrap and can be repeated.

On Windows Docker Desktop, a newly created Kind API endpoint may intermittently report
`x509: certificate signed by unknown authority` even though Kind generated an embedded CA in the
kubeconfig. Do not bypass verification with `--insecure-skip-tls-verify`. Confirm that the active
context is `kind-codinglair-taf`, regenerate it with
`kind export kubeconfig --name codinglair-taf`, and retry the ordinary CA-validated
`kubectl cluster-info --context kind-codinglair-taf`. If trust still fails, tear down the disposable
cluster, restart Docker Desktop, and create it again. The Ubuntu workflow remains the clean-room
acceptance environment.

The reference pins Keycloak and Mongo image tags for repeatable compatibility. DEVOPS-003 remains
the authority for TAF image SBOM/vulnerability evidence; production image digest promotion and
signing remain DEVOPS-005.

## NodePort and Actuator security boundary

Host port 8080 maps to control-plane NodePort 30080 so a developer can reach the MCP HTTP transport
and its Kubernetes probes. This does not expose the full Actuator surface. The existing MCP-009
Spring Security chain in `TafMcpHttpAutoConfiguration` explicitly:

- permits only `/actuator/health`, `/actuator/health/liveness`, and
  `/actuator/health/readiness` without a bearer token;
- requires an OIDC bearer JWT for `/mcp`;
- denies every other `/actuator/**` request; and
- denies every remaining application path.

In addition, `application.properties` sets `management.endpoints.web.exposure.include=health`, so
sensitive endpoints such as `/actuator/env`, `/actuator/configprops`, `/actuator/heapdump`, and
`/actuator/loggers` are not registered for web exposure. DEVOPS-004 deliberately reuses this tested
MCP-009 application boundary rather than defining a second deployment-specific filter chain.

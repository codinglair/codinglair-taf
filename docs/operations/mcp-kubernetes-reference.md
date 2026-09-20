# MCP Kubernetes reference deployment

This supported Kind profile deploys the official MCP image in its Streamable HTTP profile behind a
ClusterIP Service. It is a developer and CI reference, not a production exposure model. The
checked-in Deployment uses an immutable digest token for registry-backed release deployments.
Local bootstrap deliberately replaces that token with the exact versioned tag passed to
`kind load docker-image` and retains `imagePullPolicy: Never`. Kind's local import registers the
tag but does not guarantee a pullable `repository@digest` alias, so requesting that digest with
`Never` would be an incompatible reference. The bootstrap verifies the tag exists in the Kind
node and that the applied Deployment requests exactly the same tag. It never uses `latest`.

## Run locally

Prerequisites are Docker, Kind 0.30.0, `kubectl`, OpenSSL, Bash, and enough Docker memory to build
the Java 25 image and run Keycloak plus MCP. From the repository root:

```bash
bash deploy/kind/bootstrap-mcp.sh
bash deploy/kind/smoke-mcp.sh
TAF_KIND_CLUSTER=codinglair-taf-mcp bash deploy/kind/teardown.sh
```

PowerShell users can use `bootstrap-mcp.ps1` and `smoke-mcp.ps1`; teardown uses the existing
`teardown.ps1` after setting `TAF_KIND_CLUSTER=codinglair-taf-mcp`. Set
`TAF_KIND_SKIP_IMAGE_BUILD=true` only after loading the exact local
`codinglair/codinglair-taf-mcp:<project-version>` image you intend to qualify.

For a registry-backed release deployment, replace `IMAGE_DIGEST` in the checked-in template with
the published manifest digest and use the environment's pull policy. Do not use the local
tag/`Never` rendering outside an image-preloaded Kind cluster. This separation preserves immutable
release provenance while making the local runtime reference match what Kind actually imported.

Bootstrap generates short-lived Keycloak and smoke-client credentials in a private temporary
directory and creates Secrets through the Kubernetes API. No Secret manifest or resolved value is
rendered, logged, or retained. The MCP Secret contains only the opaque secret-provider alias
`kind-reference`; real deployments must bind their authorized provider reference by the same
mechanism. The ConfigMap owns non-sensitive Spring configuration.

## Security, storage, and lifecycle

The pod has no service-account token, runs as numeric UID/GID 10001, drops every Linux capability,
uses the runtime-default seccomp profile, forbids privilege escalation, and has a read-only root
filesystem. Writable `/tmp`, `/workspace`, and `/var/lib/taf-mcp` mounts are size-bounded
`emptyDir` volumes. Requests and limits are explicit. Default-deny NetworkPolicies allow only DNS
and same-namespace port 8080 traffic required by the reference IdP and smoke client.

The manifests express the required network boundary, but Kind's default CNI does not provide a
portable NetworkPolicy-enforcement guarantee. Environments requiring an enforced network boundary
must use a qualified policy-capable CNI and verify denied as well as allowed flows. The reference
does not claim that a policy object alone proves enforcement.

The profile deliberately has exactly one replica and uses `Recreate`. Current authoritative job,
approval, audit, and retained-result implementations are process-local; the `state` volume is only
a scoped writable boundary and does not make those contracts durable. A pod restart therefore
discards in-process state. The smoke verifies authenticated service recovery after replacement but
does not claim job or approval preservation. Do not increase replicas or claim restart durability
until those authorities are externalized and independently qualified.

Spring graceful shutdown is enabled with a 25-second application timeout and a 35-second pod
termination grace period. Startup, readiness, and liveness probes use only the public health groups
and contain no credentials. The smoke captures shutdown logs and requires both the start and
completion markers before accepting the replacement.

The MCP pod sets `enableServiceLinks: false`. Kubernetes otherwise derives legacy environment
variables from the `taf-mcp` Service, including `TAF_MCP_PORT=tcp://<cluster-ip>:8080`. That name
collides with the image launcher's numeric `TAF_MCP_PORT` setting and causes its fail-closed port
validation to exit 64 before Java starts. Disabling service links removes the implicit environment
surface and lets the launcher's documented unset/empty default bind port 8080. DNS-based Service
discovery remains available. The container port, Service target, and every probe use the named
`mcp-http` port and are contract-checked against that launcher default.

## Exposure and production adaptation

The supported manifest intentionally defines only a ClusterIP Service. An operator may add an
Ingress or Gateway in an environment-owned overlay, but must terminate TLS, preserve bearer-token
handling, restrict source networks, keep `/mcp` authenticated, and expose only the existing health
groups without a token. This repository does not prescribe a public controller, hostname,
certificate issuer, load balancer, or trust boundary.

The structural command below renders the exact checked-in topology without contacting a cluster:

```bash
mkdir -p target/inf-120-002
javac -d target/inf-120-002 build-support/ci/McpKindDeploymentContractTest.java
java -cp target/inf-120-002 McpKindDeploymentContractTest
kubectl kustomize deploy/kind/mcp-reference > target/inf-120-002/rendered-template.yaml
```

`.github/workflows/mcp-kind-reference.yml` is the clean-checkout, fresh-cluster qualification gate.
Its retained artifacts contain rendered non-secret manifests, rollout status, and bounded smoke
output. They never contain Secret objects, tokens, environment dumps, or raw configuration.

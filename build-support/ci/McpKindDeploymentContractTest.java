import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Dependency-free structural gate for the INF-120-002 MCP Kind reference. */
public final class McpKindDeploymentContractTest {
  private static final Path ROOT = Path.of("").toAbsolutePath();

  private McpKindDeploymentContractTest() {}

  public static void main(String[] args) throws Exception {
    require("deploy/kind/mcp-reference/mcp.yaml", "kind: Deployment", "kind: Service",
        "type: ClusterIP", "replicas: 1", "type: Recreate",
        "codinglair/codinglair-taf-mcp@sha256:IMAGE_DIGEST", "imagePullPolicy: Never",
        "name: mcp-http", "startupProbe:", "readinessProbe:", "livenessProbe:",
        "terminationGracePeriodSeconds: 35", "server:", "shutdown: graceful",
        "timeout-per-shutdown-phase: 25s", "secretKeyRef:", "configMap:",
        "automountServiceAccountToken: false", "enableServiceLinks: false", "runAsNonRoot: true",
        "readOnlyRootFilesystem: true", "allowPrivilegeEscalation: false",
        "drop: [\"ALL\"]", "seccompProfile:", "requests:", "limits:",
        "emptyDir:", "sizeLimit:");
    require("deploy/kind/mcp-reference/network-policy.yaml", "name: default-deny",
        "policyTypes: [Ingress, Egress]", "name: allow-dns", "name: allow-reference-traffic");
    require("deploy/kind/bootstrap-mcp.sh", "docker image inspect", "kind load docker-image",
        "ctr -n k8s.io images list",
        "sed \"s#codinglair/codinglair-taf-mcp@sha256:IMAGE_DIGEST#$IMAGE#g\"",
        "imagePullPolicy: Never", "[[ \"$applied_image\" == \"$IMAGE\" ]]", "--from-env-file",
        "--from-file", "mktemp -d", "! grep -q '^kind: Secret$'", "rollout_or_diagnose deployment/taf-mcp");
    reject("deploy/kind/bootstrap-mcp.sh", "$image_digest");
    verifyLocalKindRendering();
    require("deploy/kind/smoke-mcp.sh", "MCP discovery and execution smoke passed",
        "Commencing graceful shutdown", "Graceful shutdown complete", "delete pod",
        "rollout status deployment/taf-mcp", "test \"$old_uid\" != \"$new_uid\"");
    require("deploy/kind/teardown.ps1", "$ErrorActionPreference = 'Continue'",
        "2>&1 | ForEach-Object", "$teardownExitCode = $LASTEXITCODE",
        "$ErrorActionPreference = $previousErrorActionPreference",
        "if ($teardownExitCode -ne 0) { exit $teardownExitCode }");
    require(".github/workflows/mcp-kind-reference.yml", "permissions:", "contents: read",
        "McpKindDeploymentContractTest", "bootstrap-mcp.sh", "smoke-mcp.sh",
        "applied-manifests.yaml", "if: ${{ always() }}", "retention-days: 14");
    require("docs/operations/mcp-kubernetes-reference.md", "exactly one replica",
        "process-local", "does not make those contracts durable", "Ingress or Gateway",
        "No Secret manifest", "read-only root");
    rejectTrackedSecrets();
    reject("deploy/kind/mcp-reference/mcp.yaml", ":latest");
    verifyPortContract();
    System.out.println("MCP Kind deployment structural contract passed");
  }

  private static void require(String file, String... values) throws IOException {
    String content = Files.readString(ROOT.resolve(file));
    for (String value : values) {
      if (!content.contains(value)) {
        throw new AssertionError(file + " must contain: " + value);
      }
    }
  }

  private static void reject(String file, String value) throws IOException {
    if (Files.readString(ROOT.resolve(file)).contains(value)) {
      throw new AssertionError(file + " must not contain: " + value);
    }
  }

  private static void rejectTrackedSecrets() throws IOException {
    try (var paths = Files.walk(ROOT.resolve("deploy/kind/mcp-reference"))) {
      for (Path path : paths.filter(Files::isRegularFile).toList()) {
        reject(ROOT.relativize(path).toString(), "kind: Secret");
      }
    }
  }

  private static void verifyLocalKindRendering() throws IOException {
    String template = Files.readString(ROOT.resolve("deploy/kind/mcp-reference/mcp.yaml"));
    String localImage = "codinglair/codinglair-taf-mcp:contract-test";
    String rendered = template.replace(
        "codinglair/codinglair-taf-mcp@sha256:IMAGE_DIGEST", localImage);
    if (!rendered.contains("image: " + localImage)
        || !rendered.contains("imagePullPolicy: Never")
        || rendered.contains("IMAGE_DIGEST")) {
      throw new AssertionError(
          "Local Kind rendering must request the exact loaded tag with pull policy Never");
    }
  }

  private static void verifyPortContract() throws IOException {
    String manifest = Files.readString(ROOT.resolve("deploy/kind/mcp-reference/mcp.yaml"));
    String launcher = Files.readString(ROOT.resolve("containers/mcp/taf-mcp"));
    if (!launcher.contains("port=\"${TAF_MCP_PORT:-8080}\"")
        || !manifest.contains("containerPort: 8080")
        || !manifest.contains("targetPort: mcp-http")
        || count(manifest, "port: mcp-http") != 3
        || !manifest.contains("enableServiceLinks: false")) {
      throw new AssertionError(
          "Launcher, container, Service, and all three probes must align on MCP port 8080");
    }
  }

  private static int count(String text, String value) {
    return (text.length() - text.replace(value, "").length()) / value.length();
  }
}

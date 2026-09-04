import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Dependency-free structural gate for the DEVOPS-004 Kind reference deployment. */
public final class KindDeploymentContractTest {
  private static final Path ROOT = Path.of("").toAbsolutePath();

  private KindDeploymentContractTest() {}

  public static void main(String[] args) throws Exception {
    requireFiles();
    rejectTrackedSecretManifests();
    verifyWorkload("deploy/kind/base/control-plane.yaml", "readinessProbe:", "livenessProbe:", "startupProbe:");
    verifyWorkload("deploy/kind/base/keycloak.yaml", "readinessProbe:", "livenessProbe:", "startupProbe:");
    verifyWorkload("deploy/kind/base/mongodb.yaml", "readinessProbe:", "livenessProbe:", "volumeClaimTemplates:");
    verifyWorkload("deploy/kind/base/worker.yaml", "readinessProbe:", "livenessProbe:", "automountServiceAccountToken: false");
    requireContains("deploy/kind/base/control-plane.yaml", "secretKeyRef:", "readOnlyRootFilesystem: true", "drop: [\"ALL\"]", "resources:");
    requireContains("deploy/kind/base/config.yaml", "TAF_MCP_KIND_REFERENCE_ENABLED: \"true\"");
    requireContains("deploy/kind/base/worker.yaml", "readOnlyRootFilesystem: true", "drop: [\"ALL\"]", "emptyDir:", "sizeLimit:");
    requireContains("deploy/kind/overlays/external-mongodb/kustomization.yaml", "remove-mongodb-service.yaml", "remove-mongodb-statefulset.yaml");
    requireContains(
        "deploy/kind/bootstrap.sh",
        "TAF_EXTERNAL_MONGODB_URI is required",
        "--from-env-file",
        "--from-file",
        "--wait 120s",
        "<revision>",
        "project-version",
        "deploy Keycloak identity provider",
        "-n taf-system apply -f \"$ROOT_DIR/deploy/kind/base/keycloak.yaml\"");
    requireOrdered(
        "deploy/kind/bootstrap.sh",
        "rollout status deployment/taf-keycloak",
        "apply -f \"$TMP_DIR/taf-kind.yaml\"");
    requireContains(
        "deploy/kind/smoke.sh",
        "trap 'on_error",
        "describe job taf-mcp-smoke",
        "exitCode=",
        "--previous",
        "--sort-by='.metadata.creationTimestamp'",
        "MCP tools/list",
        "Mongo persistence/PVC validation",
        ".status.failed",
        "delete pod",
        "rollout status",
        "restart.findOne");
    requireContains(
        "deploy/kind/smoke-client.yaml",
        "OIDC token transport failed",
        "MCP initialize transport failed",
        "MCP prompts/list transport failed",
        "MCP prompts/get transport failed",
        "-w '%{http_code}'",
        "response body withheld",
        "MCP prompts/get failed:",
        "message=${error_message}",
        "MCP prompt schema:",
        "argument=${argument_name}",
        "required=${required}",
        "JSON-RPC result shape=",
        "response has no JSON-RPC result/error envelope");
    requireContains("deploy/kind/teardown.sh", "kind delete cluster", "kind get clusters");
    requireContains("deploy/kind/realm-template.json", "@SMOKE_PASSWORD@");
    requireContains("deploy/kind/realm-template.json", "\"basic\"");
    requireContains("deploy/kind/realm-template.json", "\"protocolMapper\": \"oidc-sub-mapper\"");
    requireOccurrences(
        "deploy/kind/realm-template.json", "\"include.in.token.scope\": \"true\"", 3);
    requireContains(
        ".github/workflows/kind-reference.yml",
        "kind_bin_dir=\"${RUNNER_TEMP}/kind-bin\"",
        "echo \"${kind_bin_dir}\" >> \"${GITHUB_PATH}\"",
        "name: Record Git checkout",
        "docker buildx bake --pull --no-cache --load",
        "TAF_IMAGE_VERSION=$VERSION",
        "REVISION: ${{ github.sha }}",
        "actual_revision=",
        "Reference image has no org.opencontainers.image.revision label:",
        "Reference image revision does not match the checkout:",
        "Git checkout SHA:",
        "=== Control-plane image provenance ===",
        "Kind node could not be resolved",
        "Local Docker image ID:",
        "Control-plane image has no org.opencontainers.image.revision label",
        "Control-plane image revision does not match GitHub SHA",
        "Expected pod image:",
        "Expected normalized image:",
        "Running normalized image:",
        "normalize_image_ref()",
        "printf 'docker.io/library/%s\\n'",
        "Expected control-plane image not found inside Kind:",
        "Running control-plane image is empty",
        "Running control-plane image reference does not match expected image",
        "Running control-plane imageID is empty",
        "Kind image ID differs from locally built image ID",
        "Running pod imageID differs from locally built image ID",
        "[[ \"$kind_image_id\" != \"$docker_image_id\" ]]",
        "[[ \"$running_image_id\" != \"$docker_image_id\" ]]",
        "Kind/containerd image inventory could not be inspected",
        "Kind node image ID:",
        "Running pod imageID:",
        "Control-plane image provenance verification PASSED",
        "if: ${{ failure() }}",
        "target/kind-diagnostics/smoke-termination.txt",
        "control-plane-previous.log",
        "keycloak.log");
    rejectContains(
        ".github/workflows/kind-reference.yml", "crictl inspecti \"$running_image_id\"");
    rejectContains(".github/workflows/kind-reference.yml", "/usr/local/bin/kind");
    requireContains(
        "containers/control-plane/Dockerfile",
        "taf-mcp-transport-http-${VERSION}.jar app.jar");
    rejectContains(
        "containers/control-plane/Dockerfile", "taf-mcp-transport-http-*.jar app.jar");
    System.out.println("Kind deployment structural contract passed");
  }

  private static void requireFiles() {
    List.of(
            "deploy/kind/cluster.yaml",
            "deploy/kind/base/kustomization.yaml",
            "deploy/kind/overlays/internal-mongodb/kustomization.yaml",
            "deploy/kind/overlays/external-mongodb/kustomization.yaml",
            "deploy/kind/smoke-client.yaml",
            "deploy/kind/bootstrap.sh",
            "deploy/kind/smoke.sh",
            "deploy/kind/teardown.sh",
            ".github/workflows/kind-reference.yml",
            "docs/operations/kind-reference-deployment.md")
        .forEach(
            file -> {
              if (!Files.isRegularFile(ROOT.resolve(file))) {
                throw new AssertionError("Required Kind deployment file is absent: " + file);
              }
            });
  }

  private static void rejectTrackedSecretManifests() throws IOException {
    try (var paths = Files.walk(ROOT.resolve("deploy/kind"))) {
      for (Path path : paths.filter(Files::isRegularFile).toList()) {
        String content = Files.readString(path);
        if (content.contains("kind: Secret")) {
          throw new AssertionError("Tracked Secret manifest is forbidden: " + ROOT.relativize(path));
        }
      }
    }
  }

  private static void verifyWorkload(String file, String... required) throws IOException {
    requireContains(file, required);
    requireContains(
        file,
        "automountServiceAccountToken: false",
        "requests:",
        "limits:",
        "allowPrivilegeEscalation: false",
        "seccompProfile:");
  }

  private static void requireContains(String file, String... required) throws IOException {
    String content = Files.readString(ROOT.resolve(file));
    for (String value : required) {
      if (!content.contains(value)) {
        throw new AssertionError(file + " must contain: " + value);
      }
    }
  }

  private static void rejectContains(String file, String forbidden) throws IOException {
    if (Files.readString(ROOT.resolve(file)).contains(forbidden)) {
      throw new AssertionError(file + " must not contain: " + forbidden);
    }
  }

  private static void requireOrdered(String file, String first, String second) throws IOException {
    String content = Files.readString(ROOT.resolve(file));
    int firstIndex = content.indexOf(first);
    int secondIndex = content.indexOf(second);
    if (firstIndex < 0 || secondIndex < 0 || firstIndex >= secondIndex) {
      throw new AssertionError(file + " must contain in order: " + first + " then " + second);
    }
  }

  private static void requireOccurrences(String file, String required, int expected)
      throws IOException {
    String content = Files.readString(ROOT.resolve(file));
    int occurrences = content.split(java.util.regex.Pattern.quote(required), -1).length - 1;
    if (occurrences != expected) {
      throw new AssertionError(
          file
              + " must contain "
              + expected
              + " occurrences of "
              + required
              + ", found "
              + occurrences);
    }
  }
}

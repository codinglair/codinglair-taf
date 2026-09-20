import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Dependency-free structural checks for DEVOPS-003 image and workflow controls. */
public final class ContainerSupplyChainContractTest {
  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();

  private ContainerSupplyChainContractTest() {}

  public static void main(String[] args) throws Exception {
    var control = read("containers/control-plane/Dockerfile");
    var worker = read("containers/worker/Dockerfile");
    var workerEntrypoint = read("containers/worker/entrypoint.sh");
    var mcp = read("containers/mcp/Dockerfile");
    var mcpEntrypoint = read("containers/mcp/taf-mcp");
    var mcpSmoke = read("containers/mcp/smoke.sh");
    var bake = read("docker-bake.hcl");
    var workflow = read(".github/workflows/container-images.yml");
    var publication = read(".github/workflows/mcp-image-release.yml");

    for (var dockerfile : List.of(control, worker, mcp)) {
      require(dockerfile.contains("@sha256:"), "base images must be digest pinned");
      require(dockerfile.contains("USER 1000"), "runtime must use a numeric non-root identity");
      require(dockerfile.contains("HEALTHCHECK"), "runtime must define a health check");
      require(dockerfile.contains("org.opencontainers.image.version"), "OCI version label missing");
      require(
          dockerfile.contains("package dependency:copy-dependencies")
              && !dockerfile.contains("install &&"),
          "build and dependency copy must share one revision-aware Maven reactor");
      require(!dockerfile.matches("(?s).*ARG\\s+.*(?i:password|secret|token).*"), "secret build arg forbidden");
    }
    require(workerEntrypoint.contains("umask 0077"), "worker must default to private created files");
    require(worker.contains("TAF_WORKSPACE=/workspace"), "worker workspace boundary missing");
    require(
        mcpEntrypoint.contains("exec java")
            && mcpEntrypoint.contains("stdio")
            && mcpEntrypoint.contains("streamable-http"),
        "MCP entrypoint must preserve signals and select both profiles");
    require(
        mcpEntrypoint.contains("unknown option")
            && mcpEntrypoint.contains("profile argument conflicts"),
        "MCP profile configuration must fail closed");
    require(
        mcpSmoke.contains("notifications/initialized")
            && mcpSmoke.contains("health/liveness")
            && mcpSmoke.contains("assert_launcher_port 8080")
            && mcpSmoke.contains("assert_launcher_port 18080 18080")
            && mcpSmoke.contains("assert_launcher_port 19090 18080 19090")
            && mcpSmoke.contains("0 -1 65536 invalid")
            && mcpSmoke.contains("docker stop --time 30")
            && mcpSmoke.contains("State.ExitCode")
            && mcpSmoke.contains("== 143"),
        "MCP profile and signal qualification is incomplete");
    require(
        bake.contains("control-plane") && bake.contains("worker") && bake.contains("mcp"),
        "bake targets incomplete");
    require(bake.contains("linux/arm64"), "official MCP image must declare arm64 policy");
    require(workflow.contains("load: true"), "scan and smoke image must load into Docker");
    require(
        workflow.contains("id: maven-version")
            && workflow.contains("<revision>\\([^<]*\\)</revision>")
            && workflow.contains("VERSION=${{ steps.maven-version.outputs.version }}"),
        "Maven project version must be passed to Docker builds");
    require(
        workflow.contains("REVISION=${{ github.sha }}")
            && workflow.contains("CREATED=${{ github.event.repository.updated_at }}"),
        "Docker builds must receive immutable OCI metadata");
    require(workflow.contains("sbom: false"), "Docker exporter cannot load an SBOM-attested index");
    require(
        workflow.contains("provenance: false"),
        "Docker exporter cannot load a provenance-attested index");
    require(
        workflow.contains("aquasecurity/trivy-action@v0.36.0"),
        "approved Trivy action version missing");
    require(workflow.contains("scanners: vuln"), "Trivy gate must scan vulnerabilities only");
    require(workflow.contains("severity: 'CRITICAL'"), "critical vulnerability gate missing");
    require(
        workflow.contains("Vulnerabilities[]?") && workflow.contains("exit 1"),
        "critical findings must be reported before the workflow fails");
    require(!workflow.contains("ignore-unfixed: true"), "unfixed critical findings must not receive a blanket waiver");
    require(workflow.contains("actions/upload-artifact"), "retained evidence upload missing");
    require(
        count(workflow, "target/supply-chain/${{ matrix.image }}") == 5,
        "evidence producers and validation must share the repository-root output directory");
    require(workflow.contains("path: target/supply-chain\n"), "evidence directory upload missing");
    require(workflow.contains("upload-artifact: false"), "implicit SBOM upload must be disabled");
    require(
        workflow.contains("find \"${GITHUB_WORKSPACE}\" -type f -path \"*/target/supply-chain/*\" -print"),
        "evidence diagnostic missing before upload");
    require(
        count(workflow, "test -s \"target/supply-chain/") == 2,
        "both required evidence files must be validated before upload");
    require(workflow.contains("if-no-files-found: error"), "missing required evidence must fail");
    require(
        !workflow.contains("ACTIONS_ALLOW_USE_UNSECURE_NODE_VERSION"),
        "insecure Node runtime override forbidden");
    require(
        publication.contains("codinglair/codinglair-taf-mcp:${VERSION}")
            && publication.contains("codinglair/codinglair-taf-mcp:latest"),
        "release and optional latest tags are incomplete");
    require(
        publication.contains("platforms: linux/amd64,linux/arm64")
            && publication.contains("provenance: mode=max")
            && publication.contains("sbom: true"),
        "multi-architecture attestations are incomplete");
    require(
        publication.contains("cosign sign --yes")
            && publication.contains("latest_digest")
            && publication.contains("[[ \"$latest_digest\" == \"$DIGEST\" ]]"),
        "signing or tag-digest equality evidence is incomplete");
    require(
        publication.contains("image: codinglair/codinglair-taf-mcp@${{ steps.publish.outputs.digest }}")
            && publication.contains("output-file: target/mcp-image-release/sbom.spdx.json")
            && publication.contains("upload-artifact: false"),
        "standalone digest-bound SPDX SBOM evidence is incomplete");
    require(
        publication.contains("uses: aquasecurity/trivy-action@v0.36.0")
            && publication.contains("image-ref: codinglair/codinglair-taf-mcp@${{ steps.publish.outputs.digest }}")
            && publication.contains("output: target/mcp-image-release/trivy.json")
            && publication.contains("severity: 'CRITICAL'")
            && publication.contains("exit-code: '0'"),
        "digest-bound Trivy JSON scan is incomplete");
    require(
        publication.contains("trivy --version > target/mcp-image-release/trivy-version.txt")
            && publication.contains("Vulnerabilities[]?")
            && publication.contains("exit 1"),
        "scanner metadata or explicit vulnerability gate is incomplete");
    require(
        publication.contains("test -s target/mcp-image-release/sbom.spdx.json")
            && publication.contains("test -s target/mcp-image-release/trivy.json")
            && publication.contains("test -s target/mcp-image-release/trivy-version.txt")
            && publication.contains("if: ${{ always() }}")
            && publication.contains("path: target/mcp-image-release/"),
        "release evidence validation or failure-path retention is incomplete");
    require(
        !publication.contains("COSIGN_PRIVATE_KEY")
            && !publication.contains("ignore-unfixed: true"),
        "stored signing keys and blanket vulnerability waivers are forbidden");
    System.out.println("Container supply-chain contract checks passed");
  }

  private static int count(String text, String value) {
    return (text.length() - text.replace(value, "").length()) / value.length();
  }

  private static String read(String path) throws Exception {
    return Files.readString(ROOT.resolve(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}

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
    var bake = read("docker-bake.hcl");
    var workflow = read(".github/workflows/container-images.yml");

    for (var dockerfile : List.of(control, worker)) {
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
    require(bake.contains("control-plane") && bake.contains("worker"), "bake targets incomplete");
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

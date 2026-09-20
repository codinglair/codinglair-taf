import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Dependency-free structural checks for the VER-120-002 independent qualification gate. */
public final class Ver120002QualificationContractTest {
  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();

  private Ver120002QualificationContractTest() {}

  public static void main(String[] args) throws Exception {
    String workflow = read(".github/workflows/ver-120-002-qualification.yml");
    String scanner = read("build-support/scripts/scan-ver-120-002-evidence.sh");

    require(workflow, "java-version: '25'", "CapabilityContributionCatalogTest",
        "BlueprintCompositionEngineTest", "scf.fixture.root", "sha256sum", "diff -u",
        "web-api-database", "messaging", "mobile", "-f \"$fixture/pom.xml\"",
        "containers/mcp/smoke.sh", "docker image inspect", "docker history",
        "bootstrap-mcp.sh", "smoke-mcp.sh", "MCP discovery and execution smoke passed",
        "Commencing graceful shutdown", "Graceful shutdown complete", "401",
        "scan-ver-120-002-evidence.sh", "if: ${{ always() }}", "retention-days: 30",
        "teardown.sh");
    require(scanner, "Authorization:", "receipt[_-]?[Hh]andle", "password",
        "secret", "token", "supplied canaries", "leak-scan.txt");
    require(read("deploy/kind/smoke-client.yaml"), "unauthenticated MCP",
        "unauthenticated_status", "= 401", "response body withheld");
    reject(workflow, ":latest");
    reject(workflow, "kubectl attach");
    reject(workflow, "kubectl exec");
    System.out.println("VER-120-002 qualification structural contract passed");
  }

  private static String read(String path) throws Exception {
    return Files.readString(ROOT.resolve(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
  }

  private static void require(String content, String... values) {
    for (String value : values) {
      if (!content.contains(value)) {
        throw new AssertionError("Qualification contract must contain: " + value);
      }
    }
  }

  private static void reject(String content, String value) {
    if (content.contains(value)) {
      throw new AssertionError("Qualification contract must not contain: " + value);
    }
  }
}

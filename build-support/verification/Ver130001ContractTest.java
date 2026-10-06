import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Dependency-free structural checks for the VER-130-001 local candidate gate. */
public final class Ver130001ContractTest {
  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();

  private Ver130001ContractTest() {}

  public static void main(String[] args) throws Exception {
    String gate = read("build-support/verification/ver-130-001.ps1");
    require(
        gate,
        "LOCAL_PROTOCOL_AND_STANDALONE_CONSUMER_ONLY",
        "liveAppleQualification = 'UNVERIFIED'",
        "source-tree-files.sha256",
        "Get-FileHash -Algorithm SHA256",
        "-Parchitecture,dependency-analysis",
        "-Papi-compatibility,schema-compatibility",
        "AppleDocumentationTest",
        "-Prelease-staging",
        "-Pconsumer-smoke",
        "mobile-appium",
        "examples/apple-appium-consumer/pom.xml",
        "-Dmaven.repo.local=",
        "containers/mcp/smoke.sh",
        "Invoke-McpImageProfiles",
        "MCP STDIO initialize fixture failed",
        "MCP Streamable HTTP readiness fixture failed",
        "mcpImageId",
        "A01",
        "A08");
    reject(gate, "continue-on-error");
    reject(gate, "SKIPPED");
    reject(gate, "liveAppleQualification = 'PASSED'");

    String consumerPom = read("examples/apple-appium-consumer/pom.xml");
    require(consumerPom, "taf-candidate", "${taf.candidate.repository}");
    require(read("release/consumer-smoke/mobile-appium/pom.xml"), "taf-mobile-appium", "@taf.version@");
    System.out.println("VER-130-001 local gate structural contract passed");
  }

  private static String read(String path) throws Exception {
    return Files.readString(ROOT.resolve(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
  }

  private static void require(String content, String... values) {
    for (String value : values) {
      if (!content.contains(value)) throw new AssertionError("Contract must contain: " + value);
    }
  }

  private static void reject(String content, String value) {
    if (content.contains(value)) throw new AssertionError("Contract must not contain: " + value);
  }
}

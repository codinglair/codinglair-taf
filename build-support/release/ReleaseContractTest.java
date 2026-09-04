import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Dependency-free structural and staged-artifact checks for DEVOPS-005. */
public final class ReleaseContractTest {
  private static final List<String> REQUIRED_ARTIFACTS =
      List.of(
          "codinglair-taf-bom",
          "codinglair-taf-runtime-core",
          "taf-web-playwright",
          "taf-mcp-transport-http");

  private ReleaseContractTest() {}

  public static void main(String[] args) throws IOException {
    if (args.length != 1) throw new IllegalArgumentException("staging repository path required");
    Path repository = Path.of(args[0]).toAbsolutePath().normalize();
    require(Files.isDirectory(repository), "staging repository does not exist: " + repository);
    List<String> files;
    try (var paths = Files.walk(repository)) {
      files = paths.filter(Files::isRegularFile).map(path -> path.getFileName().toString()).toList();
    }
    require(
        files.stream().noneMatch(name -> name.contains("quality-intelligence")),
        "proprietary Quality Intelligence artifact was staged");
    for (String artifact : REQUIRED_ARTIFACTS) {
      require(files.stream().anyMatch(name -> name.startsWith(artifact + "-") && name.endsWith(".pom")), artifact + " POM missing");
      if (!artifact.endsWith("-bom")) {
        require(files.stream().anyMatch(name -> name.startsWith(artifact + "-") && name.endsWith("-sources.jar")), artifact + " sources missing");
        require(files.stream().anyMatch(name -> name.startsWith(artifact + "-") && name.endsWith("-javadoc.jar")), artifact + " javadoc missing");
        require(files.stream().anyMatch(name -> name.startsWith(artifact + "-") && name.endsWith("-cyclonedx.json")), artifact + " SBOM missing");
      }
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}

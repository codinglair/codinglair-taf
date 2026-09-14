import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/** Dependency-free structural and staged-artifact checks for DEVOPS-005. */
public final class ReleaseContractTest {
  private static final Pattern REVISION =
      Pattern.compile("<revision>\\s*([^<]+?)\\s*</revision>");
  private static final List<String> REQUIRED_ARTIFACTS =
      List.of(
          "codinglair-taf-bom",
          "codinglair-taf-runtime-core",
          "taf-messaging-aws",
          "codinglair-taf-mcp",
          "taf-mcp-contracts",
          "taf-mcp-transport-stdio",
          "taf-web-playwright",
          "taf-mcp-transport-http");

  private ReleaseContractTest() {}

  public static void main(String[] args) throws IOException {
    if (args.length != 1) throw new IllegalArgumentException("staging repository path required");
    Path repository = Path.of(args[0]).toAbsolutePath().normalize();
    require(Files.isDirectory(repository), "staging repository does not exist: " + repository);
    String releaseVersion = releaseVersion();
    List<String> files;
    try (var paths = Files.walk(repository)) {
      files = paths.filter(Files::isRegularFile).map(path -> path.getFileName().toString()).toList();
    }
    require(
        files.stream().noneMatch(name -> name.contains("quality-intelligence")),
        "proprietary Quality Intelligence artifact was staged");
    require(
        files.stream().noneMatch(name -> name.contains("SNAPSHOT")),
        "snapshot artifact was staged");
    for (String artifact : REQUIRED_ARTIFACTS) {
      require(files.contains(artifact + "-" + releaseVersion + ".pom"), artifact + " release POM missing");
      if (!artifact.endsWith("-bom")) {
        require(files.contains(artifact + "-" + releaseVersion + ".jar"), artifact + " primary JAR missing");
        require(files.contains(artifact + "-" + releaseVersion + "-sources.jar"), artifact + " sources missing");
        require(files.contains(artifact + "-" + releaseVersion + "-javadoc.jar"), artifact + " javadoc missing");
        require(files.contains(artifact + "-" + releaseVersion + "-cyclonedx.json"), artifact + " SBOM missing");
      }
    }
  }

  private static String releaseVersion() throws IOException {
    String pom = Files.readString(Path.of("pom.xml"));
    var matcher = REVISION.matcher(pom);
    require(matcher.find(), "root POM revision is missing");
    String version = matcher.group(1);
    require(!version.endsWith("-SNAPSHOT"), "root POM revision is not a release version: " + version);
    return version;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}

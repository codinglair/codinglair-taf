import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

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
    List<Path> stagedFiles;
    try (var paths = Files.walk(repository)) {
      stagedFiles = paths.filter(Files::isRegularFile).toList();
    }
    List<String> files = stagedFiles.stream().map(path -> path.getFileName().toString()).toList();
    require(
        files.stream().noneMatch(name -> name.contains("quality-intelligence")),
        "proprietary Quality Intelligence artifact was staged");
    require(
        files.stream().noneMatch(name -> name.contains("SNAPSHOT")),
        "snapshot artifact was staged");
    require(
        stagedFiles.stream()
            .map(repository::relativize)
            .noneMatch(path -> path.startsWith(Path.of("com", "codinglair", "taf", "demo"))),
        "non-public demo artifact was staged");
    for (String artifact : REQUIRED_ARTIFACTS) {
      require(files.contains(artifact + "-" + releaseVersion + ".pom"), artifact + " release POM missing");
      if (!artifact.endsWith("-bom")) {
        require(files.contains(artifact + "-" + releaseVersion + ".jar"), artifact + " primary JAR missing");
        require(files.contains(artifact + "-" + releaseVersion + "-sources.jar"), artifact + " sources missing");
        require(files.contains(artifact + "-" + releaseVersion + "-javadoc.jar"), artifact + " javadoc missing");
        require(files.contains(artifact + "-" + releaseVersion + "-cyclonedx.json"), artifact + " SBOM missing");
      }
    }
    for (Path javadocJar : stagedFiles.stream()
        .filter(path -> path.getFileName().toString().endsWith("-javadoc.jar"))
        .toList()) {
      requireNoEmbeddedFonts(javadocJar);
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

  private static void requireNoEmbeddedFonts(Path javadocJar) throws IOException {
    try (var archive = new ZipFile(javadocJar.toFile())) {
      require(
          archive.stream().noneMatch(entry -> entry.getName().startsWith("resource-files/fonts/")),
          "embedded Javadoc fonts found in " + javadocJar);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}

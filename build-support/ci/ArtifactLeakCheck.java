import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Rejects unsafe or unexpectedly large text files before CI failure evidence is uploaded. */
final class ArtifactLeakCheck {
  private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
  private static final Pattern SECRET =
      Pattern.compile(
          "(?i)(AKIA[0-9A-Z]{16}|ASIA[0-9A-Z]{16}|"
              + "authorization\\s*[:=]\\s*(?!\\[REDACTED])[^\\s<]+|bearer\\s+[a-z0-9._~+/-]+=*|"
              + "credential://[^\\s<]+|canary[-_](secret|receipt|token))");
  private static final List<String> EXTENSIONS = List.of(".xml", ".txt", ".json", ".log");

  private ArtifactLeakCheck() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      throw new IllegalArgumentException("Usage: ArtifactLeakCheck <artifact-directory>");
    }
    Path root = Path.of(args[0]).toAbsolutePath().normalize();
    if (!Files.isDirectory(root)) {
      throw new IllegalArgumentException("Artifact directory does not exist: " + root);
    }
    try (Stream<Path> paths = Files.walk(root)) {
      for (Path path : paths.filter(Files::isRegularFile).toList()) {
        validate(root, path);
      }
    }
    System.out.println("Failure artifact leak check passed: " + root);
  }

  private static void validate(Path root, Path path) throws IOException {
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    if (EXTENSIONS.stream().noneMatch(name::endsWith)) {
      throw new IllegalStateException("Unsupported failure artifact type: " + root.relativize(path));
    }
    long size = Files.size(path);
    if (size > MAX_FILE_BYTES) {
      throw new IllegalStateException("Oversized failure artifact: " + root.relativize(path));
    }
    String content = Files.readString(path, StandardCharsets.UTF_8);
    if (SECRET.matcher(content).find()) {
      throw new IllegalStateException("Potential secret in failure artifact: " + root.relativize(path));
    }
  }
}

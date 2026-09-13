import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Produces a bounded, text-only, redacted CI evidence directory. */
final class ArtifactSanitizer {
  private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
  private static final List<String> EXTENSIONS = List.of(".xml", ".txt", ".json", ".log");
  private static final List<Pattern> SECRETS =
      List.of(
          Pattern.compile("(?i)bearer\\s+[a-z0-9._~+/-]+=*"),
          Pattern.compile("(?i)(authorization\\s*[:=]\\s*)[^\\s<]+"),
          Pattern.compile("(?i)(credential://)[^\\s<]+"),
          Pattern.compile("(?i)(aws[_-]?(?:access[_-]?key|secret[_-]?access[_-]?key|session[_-]?token)\\s*[:=]\\s*)[^\\s<]+"),
          Pattern.compile("(?i)canary[-_](?:secret|receipt|token)[^\\s<]*"));

  private ArtifactSanitizer() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      throw new IllegalArgumentException("Usage: ArtifactSanitizer <source> <destination>");
    }
    Path source = Path.of(args[0]).toAbsolutePath().normalize();
    Path destination = Path.of(args[1]).toAbsolutePath().normalize();
    Files.createDirectories(destination);
    if (!Files.exists(source)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(source)) {
      for (Path path : paths.filter(Files::isRegularFile).toList()) {
        copySanitized(source, destination, path);
      }
    }
  }

  private static void copySanitized(Path source, Path destination, Path path) throws IOException {
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    if (EXTENSIONS.stream().noneMatch(name::endsWith) || Files.size(path) > MAX_FILE_BYTES) {
      return;
    }
    String content = Files.readString(path, StandardCharsets.UTF_8);
    for (Pattern secret : SECRETS) {
      content = secret.matcher(content).replaceAll("[REDACTED]");
    }
    Path target = destination.resolve(source.relativize(path)).normalize();
    if (!target.startsWith(destination)) {
      throw new IllegalStateException("Artifact path escaped destination: " + path);
    }
    Files.createDirectories(target.getParent());
    Files.writeString(target, content, StandardCharsets.UTF_8);
    Files.writeString(
        target.resolveSibling(target.getFileName() + ".source-metadata.txt"),
        "source=" + source.relativize(path) + System.lineSeparator(),
        StandardCharsets.UTF_8);
  }
}

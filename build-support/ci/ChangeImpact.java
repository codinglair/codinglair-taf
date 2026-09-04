import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Classifies the complete pull-request diff for conditional verification lanes. */
final class ChangeImpact {
  record Change(String status, String oldPath, String newPath) {
    Change {
      if (status == null || status.isBlank()) {
        throw new IllegalArgumentException("Change status is required");
      }
      if ((oldPath == null || oldPath.isBlank()) && (newPath == null || newPath.isBlank())) {
        throw new IllegalArgumentException("At least one change path is required");
      }
    }

    List<String> paths() {
      var result = new ArrayList<String>();
      if (oldPath != null && !oldPath.isBlank()) {
        result.add(normalize(oldPath));
      }
      if (newPath != null && !newPath.isBlank()) {
        result.add(normalize(newPath));
      }
      return result;
    }
  }

  record Result(
      List<String> paths,
      List<String> modules,
      boolean integration,
      boolean crossModule,
      boolean browser,
      boolean appium) {}

  private ChangeImpact() {}

  static Result classify(List<Change> changes, Path repository) {
    if (changes.isEmpty()) {
      throw new IllegalArgumentException("A pull-request comparison must contain at least one change");
    }
    Set<String> paths = new LinkedHashSet<>();
    changes.stream().flatMap(change -> change.paths().stream()).forEach(paths::add);
    boolean global = paths.stream().anyMatch(ChangeImpact::isGlobal);
    List<String> modules =
        paths.stream()
            .map(path -> moduleFor(path, repository))
            .flatMap(Optional::stream)
            .filter(module -> !isStandaloneBuild(module))
            .distinct()
            .sorted()
            .toList();
    boolean productChange = paths.stream().anyMatch(ChangeImpact::isProductPath);
    boolean runtimeOrMcp =
        global
            || paths.stream()
                .anyMatch(
                    path ->
                        path.startsWith("codinglair-taf-common/")
                            || path.startsWith("codinglair-taf-runtime/")
                            || path.startsWith("codinglair-taf-mcp/")
                            || path.startsWith("taf-mcp-server/")
                            || path.startsWith("release/consumer-smoke/")
                            || path.startsWith("demos/"));
    boolean browser =
        global
            || paths.stream()
                .anyMatch(
                    path ->
                        path.startsWith("codinglair-taf-runtime/taf-web-playwright/")
                            || path.startsWith("demos/playwright-sauce-demo/"));
    boolean appium =
        global
            || paths.stream()
                .anyMatch(
                    path ->
                        path.startsWith("codinglair-taf-common/")
                            || path.startsWith(
                                "codinglair-taf-runtime/codinglair-taf-runtime-core/")
                            || path.startsWith("codinglair-taf-runtime/taf-mobile-core/")
                            || path.startsWith("codinglair-taf-runtime/taf-mobile-appium/")
                            || path.startsWith("containers/android-emulator/")
                            || isMobileQualificationContract(path));
    return new Result(
        List.copyOf(paths),
        modules,
        global || productChange,
        runtimeOrMcp,
        browser,
        appium);
  }

  static List<Change> gitDiff(Path repository, String base, String head)
      throws IOException, InterruptedException {
    if (base == null || base.isBlank() || head == null || head.isBlank()) {
      throw new IllegalArgumentException("Both comparison revisions are required");
    }
    Process process =
        new ProcessBuilder(
                "git", "diff", "--name-status", "--find-renames", "-z", base, head, "--")
            .directory(repository.toFile())
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start();
    byte[] bytes = process.getInputStream().readAllBytes();
    int exitCode = process.waitFor();
    if (exitCode != 0) {
      throw new IllegalStateException("git diff failed with exit code " + exitCode);
    }
    return parseNameStatus(bytes);
  }

  static List<Change> parseNameStatus(byte[] bytes) {
    List<String> tokens =
        java.util.Arrays.stream(new String(bytes, StandardCharsets.UTF_8).split("\\x00", -1))
            .filter(token -> !token.isEmpty())
            .toList();
    var changes = new ArrayList<Change>();
    for (int index = 0; index < tokens.size(); ) {
      String status = tokens.get(index++);
      if (status.startsWith("R") || status.startsWith("C")) {
        if (index + 1 >= tokens.size()) {
          throw new IllegalArgumentException("Incomplete rename/copy record in git diff");
        }
        changes.add(new Change(status, tokens.get(index++), tokens.get(index++)));
      } else {
        if (index >= tokens.size()) {
          throw new IllegalArgumentException("Incomplete path record in git diff");
        }
        String path = tokens.get(index++);
        changes.add(
            status.equals("D") ? new Change(status, path, null) : new Change(status, null, path));
      }
    }
    if (changes.isEmpty()) {
      throw new IllegalArgumentException("The pull-request comparison returned no changes");
    }
    return changes;
  }

  private static String normalize(String path) {
    String normalized = path.strip().replace('\\', '/');
    return normalized.startsWith("./") ? normalized.substring(2) : normalized;
  }

  private static boolean isGlobal(String path) {
    return path.equals("pom.xml")
        || path.equals("mvnw")
        || path.equals("mvnw.cmd")
        || path.equals("codinglair-taf-runtime/pom.xml")
        || path.startsWith(".github/workflows/")
        || path.startsWith(".mvn/")
        || path.startsWith("build-support/ci/");
  }

  private static boolean isProductPath(String path) {
    return path.startsWith("codinglair-taf-common/")
        || path.startsWith("codinglair-taf-runtime/")
        || path.startsWith("codinglair-taf-mcp/")
        || path.startsWith("taf-mcp-server/")
        || path.startsWith("codinglair-taf-reporting-allure/")
        || path.startsWith("codinglair-taf-bom/")
        || path.startsWith("release/consumer-smoke/")
        || path.startsWith("demos/");
  }

  private static boolean isMobileQualificationContract(String path) {
    return path.equals("docs/operations/android-emulator-image-approval-policy.md")
        || path.equals("docs/decisions/MOB-003-reference-qualification-approval.md")
        || path.equals("docs/assignments/MOB-003-handoff.md");
  }

  private static boolean isStandaloneBuild(String module) {
    return module.startsWith("release/consumer-smoke/");
  }

  private static Optional<String> moduleFor(String path, Path repository) {
    Path parent = Path.of(path).getParent();
    while (parent != null) {
      if (Files.isRegularFile(repository.resolve(parent).resolve("pom.xml"))) {
        return Optional.of(parent.toString().replace('\\', '/'));
      }
      parent = parent.getParent();
    }
    return Optional.empty();
  }

  public static void main(String[] args) throws Exception {
    Path repository = Path.of(".").toAbsolutePath().normalize();
    Path githubOutput = null;
    String base = null;
    String head = null;
    for (int index = 0; index < args.length; index++) {
      switch (args[index]) {
        case "--github-output" -> githubOutput = Path.of(args[++index]);
        case "--base" -> base = args[++index];
        case "--head" -> head = args[++index];
        default -> throw new IllegalArgumentException("Unknown argument: " + args[index]);
      }
    }
    Result result = classify(gitDiff(repository, base, head), repository);
    List<String> output =
        List.of(
            "maven_modules=" + String.join(",", result.modules()),
            "integration=" + result.integration(),
            "cross_module=" + result.crossModule(),
            "browser=" + result.browser(),
            "appium=" + result.appium(),
            "paths_b64=" + encodePaths(result.paths()));
    output.forEach(System.out::println);
    if (githubOutput != null) {
      Files.write(
          githubOutput, output, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
  }

  private static String encodePaths(List<String> paths) {
    return paths.stream()
        .map(path -> Base64.getUrlEncoder().withoutPadding().encodeToString(path.getBytes(StandardCharsets.UTF_8)))
        .reduce((left, right) -> left + "." + right)
        .orElseThrow();
  }
}

import java.nio.file.Files;
import java.nio.file.Path;

/** Configures this checkout to use the repository-managed Git hooks. */
public final class InstallGitHooks {
  private InstallGitHooks() {}

  public static void main(String[] args) {
    try {
      Process rootProcess = new ProcessBuilder("git", "rev-parse", "--show-toplevel").start();
      String output = new String(rootProcess.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      if (rootProcess.waitFor() != 0) throw new IllegalStateException("Git could not locate the repository");
      Path actual = Path.of(output.strip()).toRealPath();
      if (!Files.isRegularFile(actual.resolve("pom.xml"))
          || !Files.isRegularFile(actual.resolve(".githooks/pre-commit"))
          || !Files.isRegularFile(actual.resolve("build-support/scripts/SyncDocVersion.java"))) {
        throw new IllegalStateException(
            "refusing to configure hooks: required repository files are missing from " + actual);
      }
      Process configure = new ProcessBuilder("git", "config", "--local", "core.hooksPath", ".githooks")
          .directory(actual.toFile()).inheritIO().start();
      if (configure.waitFor() != 0) throw new IllegalStateException("git config failed");
      System.out.println("Configured " + actual);
      System.out.println("core.hooksPath=.githooks");
    } catch (Exception error) {
      System.err.println("Cannot configure Git hooks: " + error.getMessage());
      System.exit(1);
    }
  }
}

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

/** Focused temporary-repository tests for {@code SyncDocVersion.java}. */
public final class SyncDocVersionTest {
  private static final Path SCRIPT = Path.of("build-support/scripts/SyncDocVersion.java").toAbsolutePath();
  private static final String POM = """
      <project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
      <groupId>x</groupId><artifactId>x</artifactId><version>${revision}</version>
      <properties><revision>2.3.4-SNAPSHOT</revision></properties></project>
      """;
  private static int tests;

  private SyncDocVersionTest() {}

  public static void main(String[] args) throws Exception {
    testSynchronizedMultipleFilesAndValues();
    testStaleValuesReportEveryFileAndCheckIsReadOnly();
    testMalformedAndPlaceholderBothReported();
    testWriteChangesOnlyMarkedValuesAndPreservesEncodingAndNewlines();
    testWriteDoesNothingWhenSynchronized();
    testUntrackedMarkdownIsNotScanned();
    testHookContract();
    testHookUpdatesAndAbortsWithoutStaging();
    System.out.println("SyncDocVersion tests passed: " + tests);
  }

  private static void testSynchronizedMultipleFilesAndValues() throws Exception {
    try (Fixture fixture = new Fixture()) {
      fixture.markdown("one.md", "<!-- taf-version -->`2.3.4-SNAPSHOT`\n<!-- taf-version --> `2.3.4-SNAPSHOT`");
      fixture.markdown("folder/two.markdown", "<!-- taf-version -->\n`2.3.4-SNAPSHOT`");
      Result result = fixture.invoke("--check");
      equal(0, result.exitCode(), result.stderr());
    }
  }

  private static void testStaleValuesReportEveryFileAndCheckIsReadOnly() throws Exception {
    try (Fixture fixture = new Fixture()) {
      Path first = fixture.markdown("one.md", "<!-- taf-version -->`1.0.0`\n");
      Path second = fixture.markdown("two.md", "<!-- taf-version -->`1.1.0`");
      byte[] firstBefore = Files.readAllBytes(first);
      byte[] secondBefore = Files.readAllBytes(second);
      Result result = fixture.invoke("--check");
      equal(1, result.exitCode(), result.stderr());
      contains(result.stderr(), "one.md:1");
      contains(result.stderr(), "two.md:1");
      contains(result.stderr(), "SyncDocVersion.java --write");
      check(java.util.Arrays.equals(firstBefore, Files.readAllBytes(first)), "--check changed one.md");
      check(java.util.Arrays.equals(secondBefore, Files.readAllBytes(second)), "--check changed two.md");
    }
  }

  private static void testMalformedAndPlaceholderBothReported() throws Exception {
    try (Fixture fixture = new Fixture()) {
      fixture.markdown("bad.md", "orphan <!-- taf-version --> without a value\nDependency `@revision@`");
      Result result = fixture.invoke("--check");
      equal(1, result.exitCode(), result.stderr());
      contains(result.stderr(), "malformed marker");
      contains(result.stderr(), "unresolved @revision@ placeholder");
    }
  }

  private static void testWriteChangesOnlyMarkedValuesAndPreservesEncodingAndNewlines()
      throws Exception {
    try (Fixture fixture = new Fixture()) {
      Path path = fixture.markdownBytes(
          "versions.md",
          withBom(("TAF <!-- taf-version --> `1.0.0`\r\n"
              + "Dependency `9.8.7`\r\nHistorical TAF `0.9.0`\r\n").getBytes(StandardCharsets.UTF_8)));
      Result result = fixture.invoke("--write");
      equal(0, result.exitCode(), result.stderr());
      byte[] data = Files.readAllBytes(path);
      check(data[0] == (byte) 0xef && data[1] == (byte) 0xbb && data[2] == (byte) 0xbf,
          "UTF-8 BOM was not preserved");
      String text = new String(data, 3, data.length - 3, StandardCharsets.UTF_8);
      contains(text, "<!-- taf-version --> `2.3.4-SNAPSHOT`");
      contains(text, "Dependency `9.8.7`");
      contains(text, "Historical TAF `0.9.0`");
      contains(text, "\r\n");
      contains(result.stdout(), "Updated 1 Markdown file(s).");
      equal(0, fixture.invoke("--check").exitCode(), "written fixture did not validate");
    }
  }

  private static void testWriteDoesNothingWhenSynchronized() throws Exception {
    try (Fixture fixture = new Fixture()) {
      Path path = fixture.markdown("ok.md", "<!-- taf-version -->`2.3.4-SNAPSHOT`");
      FileTime before = Files.getLastModifiedTime(path);
      Result result = fixture.invoke("--write");
      equal(0, result.exitCode(), result.stderr());
      equal(before, Files.getLastModifiedTime(path), "synchronized file timestamp changed");
      contains(result.stdout(), "Updated 0 Markdown file(s).");
    }
  }

  private static void testUntrackedMarkdownIsNotScanned() throws Exception {
    try (Fixture fixture = new Fixture()) {
      Files.writeString(fixture.root.resolve("untracked.md"), "<!-- taf-version -->`stale`");
      equal(0, fixture.invoke("--check").exitCode(), "untracked Markdown was scanned");
    }
  }

  private static void testHookContract() throws Exception {
    String hook = Files.readString(Path.of(".githooks/pre-commit"));
    contains(hook, "java build-support/scripts/SyncDocVersion.java --write");
    contains(hook, "Review and stage the Markdown changes");
    check(!hook.matches("(?s).*\\n[ \\t]*git[ \\t]+add(?:[ \\t]|\\n).*"),
        "pre-commit hook must not run git add");
  }

  private static void testHookUpdatesAndAbortsWithoutStaging() throws Exception {
    try (Fixture fixture = new Fixture()) {
      Path hooks = fixture.root.resolve(".githooks");
      Files.createDirectories(hooks);
      Files.copy(Path.of(".githooks/pre-commit"), hooks.resolve("pre-commit"));
      Path markdown = fixture.markdown("hook.md", "<!-- taf-version -->`1.0.0`\n");
      Result result = command(fixture.root, shellCommand(), ".githooks/pre-commit");
      equal(1, result.exitCode(), "hook must abort after updating Markdown");
      contains(result.stderr(), "Review and stage the Markdown changes");
      contains(Files.readString(markdown), "`2.3.4-SNAPSHOT`");
      Result staged = command(fixture.root, "git", "show", ":hook.md");
      contains(staged.stdout(), "`1.0.0`");
      check(!staged.stdout().contains("`2.3.4-SNAPSHOT`"), "hook staged its generated change");
    }
  }

  private static byte[] withBom(byte[] value) {
    byte[] result = new byte[value.length + 3];
    result[0] = (byte) 0xef;
    result[1] = (byte) 0xbb;
    result[2] = (byte) 0xbf;
    System.arraycopy(value, 0, result, 3, value.length);
    return result;
  }

  private static String shellCommand() throws Exception {
    if (!System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")) {
      return "sh";
    }
    Result result = command(Path.of("."), "git", "--exec-path");
    Path candidate = Path.of(result.stdout().strip());
    for (Path directory = candidate; directory != null; directory = directory.getParent()) {
      Path shell = directory.resolve("bin/sh.exe");
      if (Files.isRegularFile(shell)) return shell.toString();
    }
    throw new IOException("cannot locate the POSIX shell installed with Git");
  }

  private static void check(boolean condition, String message) {
    tests++;
    if (!condition) throw new AssertionError(message);
  }

  private static void contains(String actual, String expected) {
    check(actual.contains(expected), "expected <" + expected + "> in <" + actual + ">");
  }

  private static void equal(Object expected, Object actual, String message) {
    check(java.util.Objects.equals(expected, actual), message + ": expected " + expected + ", got " + actual);
  }

  private record Result(int exitCode, String stdout, String stderr) {}

  private static final class Fixture implements AutoCloseable {
    private final Path root;

    private Fixture() throws Exception {
      Path fixtureParent = Path.of("target/doc-version-tests");
      Files.createDirectories(fixtureParent);
      root = Files.createTempDirectory(fixtureParent, "fixture-").toAbsolutePath();
      Path target = root.resolve("build-support/scripts");
      Files.createDirectories(target);
      Files.copy(SCRIPT, target.resolve("SyncDocVersion.java"));
      Files.writeString(root.resolve("pom.xml"), POM);
      equal(0, command(root, "git", "init", "-q").exitCode(), "git init failed");
    }

    private Path markdown(String name, String content) throws Exception {
      return markdownBytes(name, content.getBytes(StandardCharsets.UTF_8));
    }

    private Path markdownBytes(String name, byte[] content) throws Exception {
      Path path = root.resolve(name);
      Files.createDirectories(path.getParent());
      Files.write(path, content);
      equal(0, command(root, "git", "add", "--", name).exitCode(), "git add fixture failed");
      return path;
    }

    private Result invoke(String mode) throws Exception {
      return command(root, "java", root.resolve("build-support/scripts/SyncDocVersion.java").toString(), mode);
    }

    @Override
    public void close() throws IOException {
      List<Path> paths;
      try (var stream = Files.walk(root)) {
        paths = stream.sorted(java.util.Comparator.reverseOrder()).toList();
      }
      for (Path path : paths) {
        path.toFile().setWritable(true);
        Files.deleteIfExists(path);
      }
    }
  }

  private static Result command(Path directory, String... command) throws Exception {
    Process process = new ProcessBuilder(command).directory(directory.toFile()).start();
    byte[] stdout = process.getInputStream().readAllBytes();
    byte[] stderr = process.getErrorStream().readAllBytes();
    int exitCode = process.waitFor();
    return new Result(
        exitCode,
        new String(stdout, StandardCharsets.UTF_8),
        new String(stderr, StandardCharsets.UTF_8));
  }
}


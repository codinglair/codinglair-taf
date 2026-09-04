import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/** Synchronizes explicitly marked Markdown versions with the root Maven revision. */
public final class SyncDocVersion {
  private static final String MARKER = "<!-- taf-version -->";
  private static final Pattern VALUE_AFTER_MARKER =
      Pattern.compile("([ \\t\\r\\n]*)`([^`\\r\\n]+)`");
  private static final Pattern REVISION_PLACEHOLDER = Pattern.compile("@revision@", Pattern.LITERAL);
  private static final String FIX_COMMAND =
      "java build-support/scripts/SyncDocVersion.java --write";

  private SyncDocVersion() {}

  public static void main(String[] args) {
    if (args.length != 1 || !(args[0].equals("--check") || args[0].equals("--write"))) {
      System.err.println("Usage: java build-support/scripts/SyncDocVersion.java --check|--write");
      System.exit(2);
    }
    try {
      System.exit(run(args[0].equals("--write")));
    } catch (Exception error) {
      System.err.println("Documentation version synchronization failed: " + error.getMessage());
      System.exit(2);
    }
  }

  static int run(boolean write) throws Exception {
    Path root = repositoryRoot();
    String revision = rootRevision(root);
    List<Inspection> inspections = new ArrayList<>();
    List<String> failures = new ArrayList<>();

    for (Path path : markdownFiles(root)) {
      Inspection inspection = inspect(path, root, revision);
      inspections.add(inspection);
      for (Problem problem : inspection.problems()) {
        if (!write || problem.fatalOnWrite()) {
          failures.add(problem.message());
        }
      }
    }

    if (!failures.isEmpty()) {
      System.err.println("Documentation version validation failed:");
      failures.forEach(failure -> System.err.println("  " + failure));
      if (failures.stream().anyMatch(failure -> failure.contains("marked version"))) {
        System.err.println("Run: " + FIX_COMMAND);
      }
      return 1;
    }

    if (write) {
      int changed = 0;
      for (Inspection inspection : inspections) {
        if (inspection.replacements() > 0 && !inspection.original().equals(inspection.updated())) {
          byte[] content = encodeUtf8(inspection.updated(), inspection.hasBom());
          Files.write(
              inspection.path(),
              content,
              StandardOpenOption.WRITE,
              StandardOpenOption.TRUNCATE_EXISTING);
          System.out.println("Updated " + root.relativize(inspection.path()).toString().replace('\\', '/'));
          changed++;
        }
      }
      System.out.println("Updated " + changed + " Markdown file(s).");
    } else {
      System.out.println("Documentation versions match root revision " + revision + ".");
    }
    return 0;
  }

  private static Path repositoryRoot() throws IOException, InterruptedException {
    try {
      ProcessResult git = process(Path.of("").toAbsolutePath(), "git", "rev-parse", "--show-toplevel");
      if (git.exitCode() == 0) {
        Path candidate = Path.of(git.stdout().strip()).toRealPath();
        if (Files.isRegularFile(candidate.resolve("pom.xml"))) {
          return candidate;
        }
      }
    } catch (IOException ignored) {
      // Fall back to walking up from the working directory when Git is unavailable.
    }
    for (Path candidate = Path.of("").toAbsolutePath().normalize();
        candidate != null;
        candidate = candidate.getParent()) {
      if (Files.isRegularFile(candidate.resolve("pom.xml"))) {
        return candidate.toRealPath();
      }
    }
    throw new IOException("cannot locate a repository root containing pom.xml");
  }

  private static String rootRevision(Path root) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    var document = factory.newDocumentBuilder().parse(root.resolve("pom.xml").toFile());
    var properties = document.getElementsByTagNameNS("*", "properties");
    if (properties.getLength() == 0) {
      throw new IOException("root pom.xml has no <properties> element");
    }
    var revisions = ((org.w3c.dom.Element) properties.item(0)).getElementsByTagNameNS("*", "revision");
    if (revisions.getLength() == 0 || revisions.item(0).getTextContent().strip().isEmpty()) {
      throw new IOException("root pom.xml has no non-empty <revision> property");
    }
    return revisions.item(0).getTextContent().strip();
  }

  private static List<Path> markdownFiles(Path root) throws IOException, InterruptedException {
    ProcessResult git = null;
    try {
      git = process(root, "git", "-C", root.toString(), "ls-files", "-z");
    } catch (IOException ignored) {
      // Preserve standalone operation when Git is unavailable.
    }
    if (git != null && git.exitCode() == 0) {
      List<Path> paths = new ArrayList<>();
      for (String name : git.stdout().split("\\x00", -1)) {
        if (!name.isEmpty() && isMarkdown(name)) {
          Path path = root.resolve(name).normalize();
          if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            throw new IOException("tracked Markdown path is unsafe or missing: " + name);
          }
          Path realPath = path.toRealPath();
          if (!realPath.startsWith(root)) {
            throw new IOException("tracked Markdown path escapes the repository: " + name);
          }
          paths.add(realPath);
        }
      }
      paths.sort(Comparator.comparing(Path::toString));
      return paths;
    }

    try (var stream = Files.walk(root)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(path -> isMarkdown(path.getFileName().toString()))
          .filter(path -> !root.relativize(path).startsWith(".git"))
          .sorted()
          .toList();
    }
  }

  private static boolean isMarkdown(String name) {
    String lower = name.toLowerCase(java.util.Locale.ROOT);
    return lower.endsWith(".md") || lower.endsWith(".markdown");
  }

  private static Inspection inspect(Path path, Path root, String revision) throws IOException {
    byte[] bytes = Files.readAllBytes(path);
    boolean hasBom =
        bytes.length >= 3
            && bytes[0] == (byte) 0xef
            && bytes[1] == (byte) 0xbb
            && bytes[2] == (byte) 0xbf;
    String text = decodeUtf8(bytes, hasBom ? 3 : 0);
    List<Problem> problems = new ArrayList<>();
    StringBuilder updated = new StringBuilder(text.length());
    String relative = root.relativize(path).toString().replace('\\', '/');
    int cursor = 0;
    int replacements = 0;
    int markerStart;

    while ((markerStart = text.indexOf(MARKER, cursor)) >= 0) {
      int markerEnd = markerStart + MARKER.length();
      updated.append(text, cursor, markerEnd);
      Matcher value = VALUE_AFTER_MARKER.matcher(text);
      value.region(markerEnd, text.length());
      int line = lineNumber(text, markerStart);
      if (!value.lookingAt()) {
        problems.add(new Problem(relative + ":" + line
            + ": malformed marker (expected a backtick-delimited value)", true));
        cursor = markerEnd;
        continue;
      }
      String current = value.group(2);
      if (!current.equals(revision)) {
        problems.add(new Problem(relative + ":" + line + ": marked version '" + current
            + "', expected '" + revision + "'", false));
        replacements++;
      }
      updated.append(value.group(1)).append('`').append(revision).append('`');
      cursor = value.end();
    }
    updated.append(text, cursor, text.length());

    Matcher placeholder = REVISION_PLACEHOLDER.matcher(text);
    while (placeholder.find()) {
      problems.add(new Problem(relative + ":" + lineNumber(text, placeholder.start())
          + ": unresolved @revision@ placeholder", true));
    }
    return new Inspection(path, text, hasBom, updated.toString(), problems, replacements);
  }

  private static int lineNumber(String text, int offset) {
    int line = 1;
    for (int index = 0; index < offset; index++) {
      if (text.charAt(index) == '\n') line++;
    }
    return line;
  }

  private static String decodeUtf8(byte[] bytes, int offset) throws CharacterCodingException {
    return StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
        .toString();
  }

  private static byte[] encodeUtf8(String text, boolean bom) {
    byte[] value = text.getBytes(StandardCharsets.UTF_8);
    if (!bom) return value;
    byte[] result = new byte[value.length + 3];
    result[0] = (byte) 0xef;
    result[1] = (byte) 0xbb;
    result[2] = (byte) 0xbf;
    System.arraycopy(value, 0, result, 3, value.length);
    return result;
  }

  private static ProcessResult process(Path directory, String... command)
      throws IOException, InterruptedException {
    Process process = new ProcessBuilder(command).directory(directory.toFile()).start();
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    Thread stdoutReader = Thread.ofVirtual().start(() -> copy(process.getInputStream(), stdout));
    Thread stderrReader = Thread.ofVirtual().start(() -> copy(process.getErrorStream(), stderr));
    int exitCode = process.waitFor();
    stdoutReader.join();
    stderrReader.join();
    return new ProcessResult(exitCode, decodeUtf8(stdout.toByteArray(), 0));
  }

  private static void copy(InputStream source, ByteArrayOutputStream target) {
    try (source; target) {
      source.transferTo(target);
    } catch (IOException ignored) {
      // The process exit status and captured output provide the actionable result.
    }
  }

  private record ProcessResult(int exitCode, String stdout) {}

  private record Problem(String message, boolean fatalOnWrite) {}

  private record Inspection(
      Path path,
      String original,
      boolean hasBom,
      String updated,
      List<Problem> problems,
      int replacements) {}
}

import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;

/** Proves that the authoritative emulator smoke executed without a skip or failure. */
final class SmokeReportCheck {
  private SmokeReportCheck() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      throw new IllegalArgumentException("Expected the Surefire report directory");
    }
    verify(Path.of(args[0]));
    System.out.println("Android emulator smoke executed without skips or failures.");
  }

  static void verify(Path reportDirectory) throws Exception {
    Path report;
    try (var files = Files.list(reportDirectory)) {
      report =
          files
              .filter(
                  path ->
                      path.getFileName().toString().endsWith("AndroidEmulatorSmokeTest.xml"))
              .findFirst()
              .orElseThrow(
                  () -> new IllegalStateException("Android emulator smoke report is absent"));
    }
    var factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    var suite = factory.newDocumentBuilder().parse(report.toFile()).getDocumentElement();
    int tests = Integer.parseInt(suite.getAttribute("tests"));
    int skipped = Integer.parseInt(suite.getAttribute("skipped"));
    int failures = Integer.parseInt(suite.getAttribute("failures"));
    int errors = Integer.parseInt(suite.getAttribute("errors"));
    if (tests < 1 || skipped != 0 || failures != 0 || errors != 0) {
      throw new IllegalStateException(
          "Android emulator smoke must execute successfully: tests="
              + tests
              + ", skipped="
              + skipped
              + ", failures="
              + failures
              + ", errors="
              + errors);
    }
  }
}

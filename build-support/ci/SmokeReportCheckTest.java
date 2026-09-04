import java.nio.file.Files;
import java.nio.file.Path;

/** Dependency-free checks for the non-skippable emulator smoke report assertion. */
final class SmokeReportCheckTest {
  private SmokeReportCheckTest() {}

  public static void main(String[] args) throws Exception {
    Path root = Path.of("target/devops-001r-smoke-report-test");
    Files.createDirectories(root);
    write(root, 1, 0, 0, 0);
    SmokeReportCheck.verify(root);
    write(root, 1, 1, 0, 0);
    expectFailure(root);
    Files.deleteIfExists(root.resolve("TEST-AndroidEmulatorSmokeTest.xml"));
    expectFailure(root);
    System.out.println("Executed 3 emulator smoke-report scenarios");
  }

  private static void write(Path root, int tests, int skipped, int failures, int errors)
      throws Exception {
    String xml =
        "<testsuite name=\"AndroidEmulatorSmokeTest\" tests=\""
            + tests
            + "\" skipped=\""
            + skipped
            + "\" failures=\""
            + failures
            + "\" errors=\""
            + errors
            + "\"/>";
    Files.writeString(root.resolve("TEST-AndroidEmulatorSmokeTest.xml"), xml);
  }

  private static void expectFailure(Path root) throws Exception {
    try {
      SmokeReportCheck.verify(root);
      throw new AssertionError("Invalid or absent smoke report was accepted");
    } catch (IllegalStateException expected) {
      // Expected fail-closed behavior.
    }
  }
}

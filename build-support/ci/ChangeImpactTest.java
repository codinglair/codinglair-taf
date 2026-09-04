import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Dependency-free fixture runner for change-impact rules. */
final class ChangeImpactTest {
  private ChangeImpactTest() {}

  public static void main(String[] args) throws Exception {
    Path repository = Path.of(".").toAbsolutePath().normalize();
    Path fixtures = repository.resolve("build-support/ci/change-impact-fixtures.tsv");
    int executed = 0;
    for (String line : Files.readAllLines(fixtures)) {
      if (line.isBlank() || line.startsWith("#")) {
        continue;
      }
      String[] columns = line.split("\\|", -1);
      if (columns.length != 7) {
        throw new AssertionError("Malformed fixture: " + line);
      }
      ChangeImpact.Result actual = ChangeImpact.classify(changes(columns[1]), repository);
      assertEquals(columns[0], columns[2], String.join(",", actual.modules()));
      assertEquals(columns[0], columns[3], Boolean.toString(actual.integration()));
      assertEquals(columns[0], columns[4], Boolean.toString(actual.crossModule()));
      assertEquals(columns[0], columns[5], Boolean.toString(actual.browser()));
      assertEquals(columns[0], columns[6], Boolean.toString(actual.appium()));
      executed++;
    }
    verifiesNullDelimitedRenameParsing();
    verifiesMalformedDiffFailsClosed();
    System.out.println("Executed " + executed + " change-impact fixtures and 2 parser checks");
  }

  private static List<ChangeImpact.Change> changes(String value) {
    var result = new ArrayList<ChangeImpact.Change>();
    for (String encoded : value.split(";")) {
      String status = encoded.substring(0, encoded.indexOf(':'));
      String paths = encoded.substring(encoded.indexOf(':') + 1);
      if (status.startsWith("R")) {
        String[] rename = paths.split("->", 2);
        result.add(new ChangeImpact.Change(status, rename[0], rename[1]));
      } else if (status.equals("D")) {
        result.add(new ChangeImpact.Change(status, paths, null));
      } else {
        result.add(new ChangeImpact.Change(status, null, paths));
      }
    }
    return result;
  }

  private static void verifiesNullDelimitedRenameParsing() {
    byte[] bytes =
        "R100\0codinglair-taf-runtime/taf-mobile-appium/Old.java\0docs/New.java\0"
            .getBytes(StandardCharsets.UTF_8);
    List<ChangeImpact.Change> changes = ChangeImpact.parseNameStatus(bytes);
    assertEquals("rename parser", "1", Integer.toString(changes.size()));
    assertEquals(
        "rename parser old path",
        "codinglair-taf-runtime/taf-mobile-appium/Old.java",
        changes.getFirst().oldPath());
    assertEquals("rename parser new path", "docs/New.java", changes.getFirst().newPath());
  }

  private static void verifiesMalformedDiffFailsClosed() {
    try {
      ChangeImpact.parseNameStatus("R100\0only-old-path\0".getBytes(StandardCharsets.UTF_8));
      throw new AssertionError("Malformed git diff was accepted");
    } catch (IllegalArgumentException expected) {
      // Expected fail-closed behavior.
    }
  }

  private static void assertEquals(String fixture, String expected, String actual) {
    if (!expected.equals(actual)) {
      throw new AssertionError(
          fixture + ": expected <" + expected + "> but was <" + actual + ">");
    }
  }
}

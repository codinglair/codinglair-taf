import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Dependency-free regression tests for the release license policy. */
public final class ReleaseLicensePolicyTest {
  private static int assertions;

  private ReleaseLicensePolicyTest() {}

  public static void main(String[] args) throws Exception {
    assertNormalized(
        "pkg:maven/example/component@1?type=jar",
        "GPL-2.0-with-classpath-exception",
        null,
        "GPL-2.0-only WITH Classpath-exception-2.0",
        ReleaseLicensePolicy.Classification.CONDITIONALLY_ALLOWED);
    assertNormalized(
        "pkg:maven/example/component@1?type=jar",
        "GNU General Public License, version 2 with the GNU Classpath Exception",
        "https://projects.eclipse.org/license/secondary-gpl-2.0-cp",
        "GPL-2.0-only WITH Classpath-exception-2.0",
        ReleaseLicensePolicy.Classification.CONDITIONALLY_ALLOWED);
    assertClassification("GPL-2.0-only", ReleaseLicensePolicy.Classification.PROHIBITED);
    assertClassification("LGPL-2.1-only", ReleaseLicensePolicy.Classification.CONDITIONALLY_ALLOWED);
    assertClassification("LGPL-2.1-or-later", ReleaseLicensePolicy.Classification.CONDITIONALLY_ALLOWED);
    assertClassification(
        "GNU Lesser General Public License", ReleaseLicensePolicy.Classification.UNKNOWN_OR_AMBIGUOUS);
    assertClassification("new-license", ReleaseLicensePolicy.Classification.UNKNOWN_OR_AMBIGUOUS);
    for (String license : List.of("Apache-2.0", "MIT", "BSD-2-Clause", "BSD-3-Clause")) {
      assertClassification(license, ReleaseLicensePolicy.Classification.PERMISSIVE);
    }
    assertClassification("BSD licence", ReleaseLicensePolicy.Classification.UNKNOWN_OR_AMBIGUOUS);
    assertClassification("CPL", ReleaseLicensePolicy.Classification.UNKNOWN_OR_AMBIGUOUS);
    assertClassification(
        "Apache-2.0 OR MIT", ReleaseLicensePolicy.Classification.UNKNOWN_OR_AMBIGUOUS);
    assertNormalized(
        "pkg:maven/example/component@1?type=jar",
        "GNU General Public License, version 2 with the GNU Classpath Exception",
        "https://example.invalid/gpl",
        null,
        ReleaseLicensePolicy.Classification.UNKNOWN_OR_AMBIGUOUS);
    assertGate("pkg:maven/example/missing@1?type=jar", null, null, false, "component=pkg:maven/example/missing@1?type=jar");
    assertGate("pkg:maven/example/unknown@1?type=jar", "new-license", null, false, "reason=unrecognized or ambiguous declaration");
    assertGate("pkg:maven/example/gpl@1?type=jar", "GPL-2.0-only", null, false, "classification=PROHIBITED");
    assertGate("pkg:maven/example/apache@1?type=jar", "Apache-2.0", null, true, null);
    assertGate(
        "pkg:maven/example/unapproved-lgpl@1?type=jar",
        "LGPL-2.1-only",
        null,
        false,
        "conditional license has no component-specific compliance approval");
    assertGate(
        "pkg:maven/jakarta.jms/jakarta.jms-api@3.1.0?type=jar",
        "GNU General Public License, version 2 with the GNU Classpath Exception",
        "https://projects.eclipse.org/license/secondary-gpl-2.0-cp",
        true,
        null);
    assertLicensePreferenceAndAmbiguity();
    assertDelimiterParsing();
    System.out.println("Release license policy tests passed: " + assertions);
  }

  private static void assertLicensePreferenceAndAmbiguity() throws Exception {
    assertComponent(
        "pkg:maven/example/choice@1?type=jar",
        "[{\"license\":{\"id\":\"GPL-2.0-only\"}},{\"license\":{\"id\":\"MIT\"}}]",
        true,
        null);
    assertComponent(
        "pkg:maven/example/ambiguous@1?type=jar",
        "[{\"license\":{\"id\":\"MIT\"}},{\"license\":{\"name\":\"new-license\"}}]",
        false,
        "unrecognized or ambiguous declaration");
  }

  private static void assertDelimiterParsing() throws Exception {
    assertComponent(
        "pkg:maven/example/nested@1?type=jar",
        "[{\"license\":{\"id\":\"MIT\",\"comment\":\"nested { bracket } and \\\"quote\\\" and \\\\ slash\"}}]",
        true,
        null);
    assertInvalidSbom(
        "{\"metadata\":{\"component\":{\"bom-ref\":\"root\",\"licenses\":[{\"license\":{\"id\":\"MIT\"}}]}},\"components\":[{",
        "invalid components array");
    assertInvalidSbom(
        "{\"metadata\":{\"component\":{\"bom-ref\":\"root\",\"licenses\":[{\"license\":{\"id\":\"MIT\"}}]}},\"components\":{}}",
        "invalid components array");
  }

  private static void assertComponent(
      String identity, String licenses, boolean expectedPass, String diagnostic) throws Exception {
    String json = "{\"metadata\":{\"component\":{\"bom-ref\":\"root\",\"licenses\":[{\"license\":{\"id\":\"MIT\"}}]}},"
        + "\"components\":[{\"bom-ref\":\"" + identity + "\",\"licenses\":" + licenses + "}]}";
    assertSbom(json, expectedPass, diagnostic);
  }

  private static void assertInvalidSbom(String json, String diagnostic) throws Exception {
    Path fixture = Files.createTempFile("release-license-policy-invalid-", "-cyclonedx.json");
    try {
      Files.writeString(fixture, json);
      try {
        ReleaseLicensePolicy.inspectSbom(fixture, new ArrayList<>(), new LinkedHashMap<>());
        throw new AssertionError("invalid SBOM unexpectedly passed");
      } catch (IllegalStateException expected) {
        check(expected.getMessage().contains(diagnostic), "unexpected parser diagnostic: " + expected);
      }
    } finally {
      Files.deleteIfExists(fixture);
    }
  }

  private static void assertSbom(String json, boolean expectedPass, String diagnostic) throws Exception {
    Path fixture = Files.createTempFile("release-license-policy-choice-", "-cyclonedx.json");
    try {
      Files.writeString(fixture, json);
      var violations = new ArrayList<String>();
      ReleaseLicensePolicy.inspectSbom(fixture, violations, new LinkedHashMap<>());
      check(violations.isEmpty() == expectedPass, "unexpected gate result: " + violations);
      if (diagnostic != null) {
        check(violations.stream().anyMatch(value -> value.contains(diagnostic)),
            "diagnostic did not contain " + diagnostic + ": " + violations);
      }
    } finally {
      Files.deleteIfExists(fixture);
    }
  }

  private static void assertClassification(
      String raw, ReleaseLicensePolicy.Classification expected) {
    var actual = ReleaseLicensePolicy.normalize("pkg:maven/example/component@1?type=jar", raw, null);
    check(actual.classification() == expected, raw + " classified as " + actual.classification());
  }

  private static void assertNormalized(
      String identity,
      String raw,
      String url,
      String expectedExpression,
      ReleaseLicensePolicy.Classification expectedClassification) {
    var actual = ReleaseLicensePolicy.normalize(identity, raw, url);
    check(
        Objects.equals(expectedExpression, actual.normalized()),
        raw + " normalized as " + actual.normalized());
    check(
        actual.classification() == expectedClassification,
        raw + " classified as " + actual.classification());
  }

  private static void assertGate(
      String identity, String raw, String url, boolean expectedPass, String diagnostic) throws Exception {
    Path fixture = Files.createTempFile("release-license-policy-", "-cyclonedx.json");
    try {
      String license =
          raw == null
              ? ""
              : ", \"licenses\": [{\"license\": {\"name\": \""
                  + raw
                  + "\""
                  + (url == null ? "" : ", \"url\": \"" + url + "\"")
                  + "}}]";
      Files.writeString(
          fixture,
          "{\"metadata\": {\"component\": {\"type\": \"library\", \"bom-ref\": \"pkg:maven/example/root@1\", \"licenses\": [{\"license\": {\"id\": \"Apache-2.0\"}}]}}, \"components\": [{\"type\": \"library\", \"bom-ref\": \""
              + identity
              + "\""
              + license
              + "}]}");
      var violations = new ArrayList<String>();
      Map<String, String> evidence = new LinkedHashMap<>();
      ReleaseLicensePolicy.inspectSbom(fixture, violations, evidence);
      check(violations.isEmpty() == expectedPass, "unexpected gate result: " + violations);
      if (diagnostic != null) {
        check(
            violations.stream().anyMatch(message -> message.contains(diagnostic)),
            "diagnostic did not contain " + diagnostic + ": " + violations);
      }
    } finally {
      Files.deleteIfExists(fixture);
    }
  }

  private static void check(boolean condition, String message) {
    assertions++;
    if (!condition) throw new AssertionError(message);
  }
}

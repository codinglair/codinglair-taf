import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Dependency-free fixture suite for the image approval policy. */
final class ImagePolicyTest {
  private ImagePolicyTest() {}

  public static void main(String[] args) throws Exception {
    Path fixtures = Path.of("build-support/ci/image-policy-fixtures");
    List<String> passes = List.of("pass-guest.properties", "pass-builder.properties");
    List<String> rejects = List.of(
        "reject-control-critical.properties", "reject-control-high.properties",
        "reject-guest-escape.properties", "reject-builder-unverified.properties",
        "reject-unknown.properties", "reject-missing-sbom.properties",
        "reject-missing-provenance.properties", "reject-invalid-digest.properties",
        "reject-mismatched-digest.properties",
        "reject-missing-artifact-checksum.properties", "reject-license-count.properties",
        "reject-license.properties", "reject-malformed.properties", "reject-secret.properties",
        "reject-prior-api34.properties", "reject-prior-api30.properties");
    for (String fixture : passes) assertResult(fixtures, fixture, true);
    for (String fixture : rejects) assertResult(fixtures, fixture, false);
    var grouped = ImagePolicy.evaluate(loadFixture(fixtures, "pass-guest.properties"));
    if (!grouped.findings().get(ImagePolicy.Plane.ANDROID_GUEST).equals(List.of("CVE-GUEST")))
      throw new AssertionError("Structured output did not retain guest classification");
    String secretOutput = ImagePolicy.structured(ImagePolicy.evaluate("token=canary-value"));
    if (secretOutput.contains("canary-value")) throw new AssertionError("Secret-like value leaked to output");
    System.out.println("Executed 20 image-policy scenarios");
  }

  private static void assertResult(Path fixtures, String fixture, boolean expected) throws Exception {
    boolean actual = ImagePolicy.evaluate(loadFixture(fixtures, fixture)).passed();
    if (actual != expected) throw new AssertionError(fixture + " expected pass=" + expected + " but was " + actual);
  }

  private static String loadFixture(Path fixtures, String fixture) throws Exception {
    String overlay = Files.readString(fixtures.resolve(fixture));
    if (fixture.equals("pass-guest.properties")) return overlay;
    if (fixture.equals("reject-malformed.properties") || fixture.equals("reject-secret.properties")
        || fixture.startsWith("reject-prior-")) return overlay;
    var values = new java.util.LinkedHashMap<String, String>();
    for (String line : Files.readAllLines(fixtures.resolve("pass-guest.properties"))) put(values, line);
    for (String line : overlay.lines().toList()) {
      if (line.startsWith("!remove=")) values.remove(line.substring("!remove=".length()));
      else put(values, line);
    }
    return values.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
        .collect(java.util.stream.Collectors.joining("\n"));
  }

  private static void put(java.util.Map<String, String> values, String line) {
    if (line.isBlank() || line.startsWith("#")) return;
    int separator = line.indexOf('=');
    values.put(line.substring(0, separator), line.substring(separator + 1));
  }
}

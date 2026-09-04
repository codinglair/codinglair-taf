import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Fail-closed Android emulator image evidence evaluator. */
final class ImagePolicy {
  enum Plane { BUILDER, CONTROL_PLANE, ANDROID_GUEST, UNKNOWN }

  record Decision(boolean passed, Map<Plane, List<String>> findings, List<String> reasons) {}

  private static final Pattern DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");
  private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
  private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40,64}");
  private static final Pattern SECRET = Pattern.compile(
      "(?i)(authorization\\s*[:=]|bearer\\s+[a-z0-9._-]+|password\\s*[:=]|secret\\s*[:=]|token\\s*[:=]|-----BEGIN [A-Z ]*PRIVATE KEY-----)");
  private static final Set<String> LICENSE_FAILURES =
      Set.of("PROHIBITED", "UNKNOWN", "UNREVIEWED", "CONFLICTING");

  private ImagePolicy() {}

  static Decision evaluate(String raw) {
    var byPlane = new EnumMap<Plane, List<String>>(Plane.class);
    for (Plane plane : Plane.values()) byPlane.put(plane, new ArrayList<>());
    var reasons = new ArrayList<String>();
    if (SECRET.matcher(raw).find()) {
      reasons.add("Evidence contains a secret-like value; content was not retained");
      return new Decision(false, byPlane, List.copyOf(reasons));
    }

    Map<String, String> evidence;
    try {
      evidence = parse(raw);
    } catch (IllegalArgumentException exception) {
      reasons.add("Malformed evidence: " + exception.getMessage());
      return new Decision(false, byPlane, List.copyOf(reasons));
    }

    require(evidence, reasons, "candidate.name", "candidate.purpose", "target.os", "target.arch",
        "image.digest", "image.scannedDigest", "builder.baseDigests", "runtime.baseDigest", "source.repository",
        "source.commit", "sbom.location", "sbom.sha256", "provenance.location",
        "provenance.verified", "scanner.name", "scanner.version", "scanner.database",
        "scanner.databaseDate", "notice.location", "androidSdkLicense.acceptanceRecord");
    exact(evidence, reasons, "image.digest", DIGEST);
    exact(evidence, reasons, "image.scannedDigest", DIGEST);
    if (!value(evidence, "image.digest").equals(value(evidence, "image.scannedDigest")))
      reasons.add("Scanned image digest does not match the declared candidate digest");
    exact(evidence, reasons, "runtime.baseDigest", DIGEST);
    for (String digest : value(evidence, "builder.baseDigests").split(",", -1)) {
      if (!DIGEST.matcher(digest.strip()).matches()) reasons.add("Invalid builder base digest");
    }
    exact(evidence, reasons, "sbom.sha256", SHA256);
    exact(evidence, reasons, "source.commit", COMMIT);
    bool(evidence, reasons, "provenance.verified", true);
    bool(evidence, reasons, "builder.basesScanned", true);
    bool(evidence, reasons, "policy.rejectFixableHigh", true);
    bool(evidence, reasons, "controls.adbPublic", false);
    requireValue(evidence, reasons, "controls.appiumBinding", "APPROVED");
    bool(evidence, reasons, "controls.productionCredentials", false);
    bool(evidence, reasons, "controls.productionData", false);
    bool(evidence, reasons, "controls.ephemeralData", true);
    bool(evidence, reasons, "controls.productionConnectivity", false);
    bool(evidence, reasons, "controls.privileged", false);

    int artifacts = count(evidence, reasons, "artifact.count");
    for (int index = 0; index < artifacts; index++) {
      String prefix = "artifact." + index + ".";
      require(evidence, reasons, prefix + "url", prefix + "version", prefix + "sha256");
      exact(evidence, reasons, prefix + "sha256", SHA256);
    }

    int licenses = count(evidence, reasons, "license.count");
    if (licenses == 0) reasons.add("Exact license inventory is required; a record count is insufficient");
    for (int index = 0; index < licenses; index++) {
      String prefix = "license." + index + ".";
      require(evidence, reasons, prefix + "component", prefix + "exact", prefix + "obligation",
          prefix + "scope", prefix + "disposition");
      String disposition = value(evidence, prefix + "disposition").toUpperCase(Locale.ROOT);
      if (LICENSE_FAILURES.contains(disposition) || !disposition.equals("ALLOWED")) {
        reasons.add("License " + index + " has non-approved disposition " + safe(disposition));
      }
    }

    int findingCount = count(evidence, reasons, "finding.count");
    for (int index = 0; index < findingCount; index++) {
      evaluateFinding(evidence, index, byPlane, reasons);
    }
    return new Decision(reasons.isEmpty(), immutable(byPlane), List.copyOf(reasons));
  }

  private static void evaluateFinding(Map<String, String> evidence, int index,
      Map<Plane, List<String>> byPlane, List<String> reasons) {
    String prefix = "finding." + index + ".";
    require(evidence, reasons, prefix + "id", prefix + "component", prefix + "severity",
        prefix + "fixAvailable", prefix + "plane", prefix + "classificationEvidence");
    String id = safe(value(evidence, prefix + "id"));
    Plane plane;
    try { plane = Plane.valueOf(value(evidence, prefix + "plane")); }
    catch (IllegalArgumentException exception) { plane = Plane.UNKNOWN; }
    byPlane.get(plane).add(id);
    String proof = value(evidence, prefix + "classificationEvidence");
    if (!(proof.startsWith("path:") || proof.startsWith("layer:") || proof.startsWith("sbom:")
        || proof.startsWith("mapping:"))) {
      reasons.add(id + " lacks reproducible plane evidence");
    }
    if (plane == Plane.UNKNOWN) reasons.add(id + " has UNKNOWN execution plane");
    boolean fixable = booleanValue(evidence, prefix + "fixAvailable");
    String severity = value(evidence, prefix + "severity");
    if (plane == Plane.CONTROL_PLANE && fixable
        && (severity.equals("CRITICAL") || severity.equals("HIGH"))) {
      reasons.add(id + " is an applicable fixable " + severity + " control-plane finding");
    }
    if (plane == Plane.BUILDER) {
      if (booleanValue(evidence, prefix + "presentInRuntime"))
        reasons.add(id + " builder component is present in the runtime filesystem");
      if (!booleanValue(evidence, prefix + "outputVerified"))
        reasons.add(id + " builder output is not cryptographically verified");
    }
    if (plane == Plane.ANDROID_GUEST) {
      boolean isolated = !booleanValue(evidence, prefix + "hostEscapePossible")
          && !booleanValue(evidence, prefix + "crossServicePossible")
          && !booleanValue(evidence, prefix + "credentialOrDataImpact")
          && booleanValue(evidence, prefix + "intrinsicToSelectedAndroid");
      if (!isolated) reasons.add(id + " fails the Android guest threat model");
    }
  }

  private static Map<String, String> parse(String raw) {
    var result = new LinkedHashMap<String, String>();
    int lineNumber = 0;
    for (String original : raw.split("\\R", -1)) {
      lineNumber++;
      String line = original.strip();
      if (line.isEmpty() || line.startsWith("#")) continue;
      int separator = line.indexOf('=');
      if (separator <= 0) throw new IllegalArgumentException("invalid line " + lineNumber);
      String key = line.substring(0, separator).strip();
      String value = line.substring(separator + 1).strip();
      if (key.isEmpty() || value.isEmpty()) throw new IllegalArgumentException("empty field at line " + lineNumber);
      if (result.putIfAbsent(key, value) != null) throw new IllegalArgumentException("duplicate field " + key);
    }
    return result;
  }

  private static int count(Map<String, String> evidence, List<String> reasons, String key) {
    try {
      int count = Integer.parseInt(value(evidence, key));
      if (count < 0 || count > 10_000) throw new NumberFormatException();
      return count;
    } catch (NumberFormatException exception) {
      reasons.add("Invalid or missing " + key);
      return 0;
    }
  }

  private static void require(Map<String, String> evidence, List<String> reasons, String... keys) {
    for (String key : keys) if (!evidence.containsKey(key)) reasons.add("Missing required evidence: " + key);
  }

  private static void exact(Map<String, String> evidence, List<String> reasons, String key, Pattern pattern) {
    if (!pattern.matcher(value(evidence, key)).matches()) reasons.add("Invalid or missing " + key);
  }

  private static void bool(Map<String, String> evidence, List<String> reasons, String key, boolean expected) {
    String actual = value(evidence, key);
    if (!(actual.equals("true") || actual.equals("false")) || Boolean.parseBoolean(actual) != expected)
      reasons.add(key + " must be " + expected);
  }

  private static boolean booleanValue(Map<String, String> evidence, String key) {
    return Boolean.parseBoolean(value(evidence, key));
  }

  private static void requireValue(Map<String, String> evidence, List<String> reasons, String key, String expected) {
    if (!value(evidence, key).equals(expected)) reasons.add(key + " must be " + expected);
  }

  private static String value(Map<String, String> evidence, String key) { return evidence.getOrDefault(key, ""); }
  private static String safe(String text) { return text.replaceAll("[^A-Za-z0-9_.:/-]", "?"); }

  private static Map<Plane, List<String>> immutable(Map<Plane, List<String>> source) {
    var result = new EnumMap<Plane, List<String>>(Plane.class);
    source.forEach((key, value) -> result.put(key, List.copyOf(value)));
    return Map.copyOf(result);
  }

  static String sha256(Path path) throws Exception {
    byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
    return java.util.HexFormat.of().formatHex(digest);
  }

  static String structured(Decision decision) {
    var output = new StringBuilder("{\"result\":\"").append(decision.passed() ? "PASS" : "REJECT").append("\",\"findings\":{");
    boolean firstPlane = true;
    for (Plane plane : Plane.values()) {
      if (!firstPlane) output.append(',');
      firstPlane = false;
      output.append('\"').append(plane).append("\":[");
      for (int index = 0; index < decision.findings().get(plane).size(); index++) {
        if (index > 0) output.append(',');
        output.append('\"').append(decision.findings().get(plane).get(index)).append('\"');
      }
      output.append(']');
    }
    output.append("},\"reasons\":[");
    for (int index = 0; index < decision.reasons().size(); index++) {
      if (index > 0) output.append(',');
      output.append('\"').append(decision.reasons().get(index).replace("\\", "\\\\").replace("\"", "\\\"")).append('\"');
    }
    return output.append("]}").toString();
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 1) throw new IllegalArgumentException("Usage: ImagePolicy <evidence.properties>");
    Decision decision = evaluate(Files.readString(Path.of(args[0]), StandardCharsets.UTF_8));
    System.out.println(structured(decision));
    if (!decision.passed()) System.exit(1);
  }
}

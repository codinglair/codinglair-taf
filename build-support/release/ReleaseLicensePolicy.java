import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dependency-free, fail-closed CycloneDX license policy gate for community releases. */
public final class ReleaseLicensePolicy {
  enum Classification {
    PERMISSIVE,
    CONDITIONALLY_ALLOWED,
    PROHIBITED,
    UNKNOWN_OR_AMBIGUOUS
  }

  record LicenseDeclaration(
      String raw, String url, String normalized, Classification classification, String reason) {}

  private record ConditionalApproval(Set<String> expressions, String obligations) {}

  private static final Set<String> PERMISSIVE =
      Set.of(
          "0BSD",
          "Apache-2.0",
          "BSD-2-Clause",
          "BSD-3-Clause",
          "BSD-4-Clause",
          "CC0-1.0",
          "EDL-1.0",
          "ISC",
          "MIT",
          "MIT-0");
  private static final Set<String> CONDITIONAL =
      Set.of(
          "CPL-1.0",
          "EPL-1.0",
          "EPL-2.0",
          "GPL-2.0-only WITH Classpath-exception-2.0",
          "LGPL-2.1-only",
          "LGPL-2.1-or-later",
          "LGPL-3.0-only",
          "LGPL-3.0-or-later",
          "MPL-2.0");
  private static final Map<String, ConditionalApproval> CONDITIONAL_APPROVALS = approvals();
  private static final Pattern FIELD_PATTERN_TEMPLATE =
      Pattern.compile("\\\"%s\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

  private ReleaseLicensePolicy() {}

  public static void main(String[] args) throws IOException {
    if (args.length != 1) throw new IllegalArgumentException("staging repository path required");
    Path repository = Path.of(args[0]).toAbsolutePath().normalize();
    require(Files.isDirectory(repository), "staging repository does not exist: " + repository);

    List<Path> sboms;
    try (var paths = Files.walk(repository)) {
      sboms =
          paths
              .filter(Files::isRegularFile)
              .filter(path -> path.getFileName().toString().endsWith("-cyclonedx.json"))
              .sorted()
              .toList();
    }
    require(!sboms.isEmpty(), "no CycloneDX JSON SBOMs found");

    List<String> violations = new ArrayList<>();
    Map<String, String> conditionalEvidence = new LinkedHashMap<>();
    for (Path sbom : sboms) inspectSbom(sbom, violations, conditionalEvidence);
    require(
        violations.isEmpty(),
        "license policy violations:" + System.lineSeparator() + String.join(System.lineSeparator(), violations));
    System.out.printf(
        "License policy passed: %d SBOMs, %d conditionally allowed components.%n",
        sboms.size(), conditionalEvidence.size());
    conditionalEvidence.values().stream().sorted().forEach(System.out::println);
  }

  static void inspectSbom(
      Path sbom, List<String> violations, Map<String, String> conditionalEvidence) throws IOException {
    String json = Files.readString(sbom);
    int metadataKey = json.indexOf("\"metadata\"");
    int metadataStart = json.indexOf('{', metadataKey);
    int metadataEnd = matchingDelimiter(json, metadataStart, '{', '}');
    require(metadataKey >= 0 && metadataEnd > metadataStart, sbom + ": metadata object missing");
    int componentsKey = json.indexOf("\"components\"", metadataEnd);
    if (componentsKey < 0) {
      int componentKey = json.indexOf("\"component\"", metadataStart);
      int componentStart = json.indexOf('{', componentKey);
      int componentEnd = matchingDelimiter(json, componentStart, '{', '}');
      require(
          componentKey >= 0 && componentEnd > componentStart && componentEnd <= metadataEnd,
          sbom + ": component inventory missing");
      inspectComponent(
          sbom, json.substring(componentStart, componentEnd + 1), violations, conditionalEvidence);
      return;
    }

    int arrayStart = json.indexOf('[', componentsKey);
    int arrayEnd = matchingDelimiter(json, arrayStart, '[', ']');
    require(arrayStart >= 0 && arrayEnd > arrayStart, sbom + ": invalid components array");
    int cursor = arrayStart + 1;
    int count = 0;
    while (cursor < arrayEnd) {
      int objectStart = nextEntry(json, cursor, arrayEnd);
      if (objectStart >= arrayEnd) break;
      require(json.charAt(objectStart) == '{', sbom + ": invalid component entry");
      int objectEnd = matchingDelimiter(json, objectStart, '{', '}');
      require(objectEnd > objectStart && objectEnd <= arrayEnd, sbom + ": invalid component object");
      inspectComponent(
          sbom, json.substring(objectStart, objectEnd + 1), violations, conditionalEvidence);
      count++;
      cursor = objectEnd + 1;
    }
    require(count > 0, sbom + ": components array is empty");
  }

  private static void inspectComponent(
      Path sbom,
      String component,
      List<String> violations,
      Map<String, String> conditionalEvidence) {
    String componentIdentity = field(component, "bom-ref");
    if (componentIdentity == null) componentIdentity = field(component, "name");
    final String identity =
        componentIdentity == null ? "unidentified component" : componentIdentity;
    List<LicenseDeclaration> declarations = declarations(identity, component);
    if (declarations.isEmpty()) {
      violations.add(diagnostic(sbom, identity, null, "missing license declaration"));
      return;
    }

    declarations.stream()
        .filter(item -> item.classification() == Classification.UNKNOWN_OR_AMBIGUOUS)
        .forEach(item -> violations.add(diagnostic(sbom, identity, item, item.reason())));
    if (declarations.stream()
        .anyMatch(item -> item.classification() == Classification.UNKNOWN_OR_AMBIGUOUS)) return;

    LicenseDeclaration selected =
        declarations.stream()
            .filter(item -> item.classification() == Classification.PERMISSIVE)
            .findFirst()
            .orElseGet(
                () ->
                    declarations.stream()
                        .filter(item -> item.classification() == Classification.CONDITIONALLY_ALLOWED)
                        .findFirst()
                        .orElse(null));
    if (selected == null) {
      LicenseDeclaration prohibited = declarations.getFirst();
      violations.add(
          diagnostic(sbom, identity, prohibited, "no permitted license choice; " + prohibited.reason()));
      return;
    }
    if (selected.classification() == Classification.CONDITIONALLY_ALLOWED) {
      ConditionalApproval approval = CONDITIONAL_APPROVALS.get(identity);
      if (approval == null || !approval.expressions().contains(selected.normalized())) {
        violations.add(
            diagnostic(
                sbom,
                identity,
                selected,
                "conditional license has no component-specific compliance approval"));
        return;
      }
      conditionalEvidence.put(
          identity,
          identity
              + " | raw="
              + selected.raw()
              + " | normalized="
              + selected.normalized()
              + " | classification=CONDITIONALLY_ALLOWED | obligations="
              + approval.obligations());
    }
  }

  private static List<LicenseDeclaration> declarations(String identity, String component) {
    int licensesKey = component.indexOf("\"licenses\"");
    if (licensesKey < 0) return List.of();
    int start = component.indexOf('[', licensesKey);
    int end = matchingDelimiter(component, start, '[', ']');
    if (start < 0 || end <= start) return List.of();
    List<LicenseDeclaration> result = new ArrayList<>();
    int cursor = start + 1;
    while (cursor < end) {
      int objectStart = nextEntry(component, cursor, end);
      if (objectStart >= end) break;
      if (component.charAt(objectStart) != '{') return List.of();
      int objectEnd = matchingDelimiter(component, objectStart, '{', '}');
      if (objectEnd <= objectStart || objectEnd > end) return List.of();
      String choice = component.substring(objectStart, objectEnd + 1);
      String raw = field(choice, "expression");
      if (raw == null) raw = field(choice, "id");
      if (raw == null) raw = field(choice, "name");
      String url = field(choice, "url");
      if (raw != null) result.add(normalize(identity, raw, url));
      cursor = objectEnd + 1;
    }
    return result;
  }

  static LicenseDeclaration normalize(String identity, String raw, String url) {
    String normalized = canonical(identity, raw, url);
    Classification classification;
    String reason;
    if (normalized == null) {
      classification = Classification.UNKNOWN_OR_AMBIGUOUS;
      reason = "unrecognized or ambiguous declaration";
    } else if (PERMISSIVE.contains(normalized)) {
      classification = Classification.PERMISSIVE;
      reason = "recognized permissive SPDX license";
    } else if (CONDITIONAL.contains(normalized)) {
      classification = Classification.CONDITIONALLY_ALLOWED;
      reason = "copyleft or exception-bearing license requires recorded obligations";
    } else if (isProhibited(normalized)) {
      classification = Classification.PROHIBITED;
      reason = "license is prohibited without an approved exception or alternative";
    } else {
      classification = Classification.UNKNOWN_OR_AMBIGUOUS;
      reason = "normalized expression is not classified";
    }
    return new LicenseDeclaration(raw, url, normalized, classification, reason);
  }

  private static String canonical(String identity, String raw, String url) {
    if (PERMISSIVE.contains(raw) || CONDITIONAL.contains(raw)) return raw;
    if (raw.equals("GPL-2.0-with-classpath-exception")) {
      return "GPL-2.0-only WITH Classpath-exception-2.0";
    }
    if (raw.equals("GNU General Public License, version 2 with the GNU Classpath Exception")
        && url != null
        && url.contains("secondary-gpl-2.0-cp")) {
      return "GPL-2.0-only WITH Classpath-exception-2.0";
    }
    if (identity.equals("pkg:maven/com.rabbitmq/amqp-client@5.30.0?type=jar")
        && raw.equals("AL 2.0")
        && url != null
        && url.contains("apache.org/licenses/LICENSE-2.0")) return "Apache-2.0";
    if (identity.equals("pkg:maven/org.antlr/antlr-runtime@3.5.3?type=jar")
        && raw.equals("BSD licence")
        && url != null
        && url.contains("antlr.org/license.html")) return "BSD-3-Clause";
    if (identity.equals("pkg:maven/wsdl4j/wsdl4j@1.6.3?type=jar")
        && raw.equals("CPL")
        && url != null
        && url.contains("cpl1.0")) return "CPL-1.0";
    if (identity.equals("pkg:maven/org.cryptacular/cryptacular@1.2.7?type=jar")
        && raw.equals("GNU Lesser General Public License")
        && url != null
        && url.contains("lgpl-3.0")) return "LGPL-3.0-only";
    if (raw.equals("GPL v2") || raw.equals("GPL-2.0") || raw.equals("GPL-2.0-only")) {
      return "GPL-2.0-only";
    }
    return null;
  }

  private static boolean isProhibited(String expression) {
    return expression.equals("GPL-2.0-only")
        || expression.startsWith("AGPL-")
        || expression.startsWith("SSPL-")
        || expression.startsWith("BUSL-")
        || expression.equals("Proprietary");
  }

  private static String diagnostic(
      Path sbom, String identity, LicenseDeclaration declaration, String reason) {
    if (declaration == null) {
      return sbom.getFileName()
          + " | component="
          + identity
          + " | raw=<missing> | normalized=<none> | classification=UNKNOWN_OR_AMBIGUOUS | reason="
          + reason;
    }
    return sbom.getFileName()
        + " | component="
        + identity
        + " | raw="
        + declaration.raw()
        + " | url="
        + (declaration.url() == null ? "<missing>" : declaration.url())
        + " | normalized="
        + (declaration.normalized() == null ? "<none>" : declaration.normalized())
        + " | classification="
        + declaration.classification()
        + " | reason="
        + reason;
  }

  private static Map<String, ConditionalApproval> approvals() {
    Map<String, ConditionalApproval> result = new LinkedHashMap<>();
    String independent =
        "independent unmodified Java library; retain license/copyright notices; publish source for distributed modifications; formal legal review before modification or bundling";
    result.put(
        "pkg:maven/ch.qos.logback/logback-classic@1.5.34?type=jar",
        new ConditionalApproval(Set.of("EPL-2.0", "LGPL-2.1-only"), independent));
    result.put(
        "pkg:maven/ch.qos.logback/logback-core@1.5.34?type=jar",
        new ConditionalApproval(Set.of("EPL-2.0", "LGPL-2.1-only"), independent));
    result.put(
        "pkg:maven/com.github.spotbugs/spotbugs-annotations@4.9.8?type=jar",
        new ConditionalApproval(
            Set.of("LGPL-2.1-only"),
            independent + "; preserve user ability to replace/relink the library"));
    result.put(
        "pkg:maven/jakarta.annotation/jakarta.annotation-api@3.0.0?type=jar",
        new ConditionalApproval(
            Set.of("EPL-2.0", "GPL-2.0-only WITH Classpath-exception-2.0"), independent));
    result.put(
        "pkg:maven/jakarta.jms/jakarta.jms-api@3.1.0?type=jar",
        new ConditionalApproval(
            Set.of("EPL-2.0", "GPL-2.0-only WITH Classpath-exception-2.0"),
            independent + "; Classpath Exception applies only to the declared Jakarta JMS artifact"));
    result.put(
        "pkg:maven/wsdl4j/wsdl4j@1.6.3?type=jar",
        new ConditionalApproval(
            Set.of("CPL-1.0"),
            independent + "; make WSDL4J source availability and CPL-1.0 text visible on redistribution"));
    return Map.copyOf(result);
  }

  private static String field(String json, String name) {
    Pattern pattern =
        Pattern.compile(String.format(FIELD_PATTERN_TEMPLATE.pattern(), Pattern.quote(name)));
    Matcher matcher = pattern.matcher(json);
    return matcher.find() ? matcher.group(1) : null;
  }

  private static int nextEntry(String value, int start, int limit) {
    int cursor = start;
    while (cursor < limit
        && (Character.isWhitespace(value.charAt(cursor)) || value.charAt(cursor) == ',')) cursor++;
    return cursor;
  }

  private static int matchingDelimiter(String value, int start, char open, char close) {
    if (start < 0 || start >= value.length() || value.charAt(start) != open) return -1;
    int depth = 0;
    boolean inString = false;
    boolean escaped = false;
    for (int index = start; index < value.length(); index++) {
      char character = value.charAt(index);
      if (inString) {
        if (escaped) escaped = false;
        else if (character == '\\') escaped = true;
        else if (character == '"') inString = false;
      } else if (character == '"') inString = true;
      else if (character == open) depth++;
      else if (character == close && --depth == 0) return index;
    }
    return -1;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/** Dependency-free regression checks for DEP-132-001 dependency and scan policy. */
public final class JacksonDependencyPolicyTest {
  private static int assertions;

  private JacksonDependencyPolicyTest() {}

  public static void main(String[] args) throws Exception {
    String root = Files.readString(Path.of("pom.xml"));
    String bom = Files.readString(Path.of("codinglair-taf-bom", "pom.xml"));
    String stdio = Files.readString(Path.of("taf-mcp-server", "taf-mcp-transport-stdio", "pom.xml"));
    String http = Files.readString(Path.of("taf-mcp-server", "taf-mcp-transport-http", "pom.xml"));
    String scan = Files.readString(Path.of("build-support", "scripts", "scan-jackson-surfaces.sh"));

    check(property(root, "jackson.version").equals("2.21.7"), "Jackson runtime is not the approved patched LTS version");
    check(property(root, "jackson.annotations.version").equals("2.21"), "Jackson annotations are not converged on 2.21");
    check(property(root, "jackson3.version").equals("3.1.7"), "Jackson 3 runtime is not the approved patched LTS version");
    check(property(root, "cucumber.version").equals("7.34.9"), "Cucumber still embeds a vulnerable relocated Jackson runtime");
    for (String artifact : List.of("jackson-core", "jackson-databind", "jackson-dataformat-csv", "jackson-dataformat-yaml")) {
      check(managedWithVersion(bom, artifact, "${jackson.version}"), artifact + " is not explicitly managed by the published BOM");
    }
    check(managedWithVersion(bom, "jackson-annotations", "${jackson.annotations.version}"), "annotations are not explicitly managed by the published BOM");
    for (String artifact : List.of("jackson-core", "jackson-databind", "jackson-dataformat-yaml")) {
      check(managedWithVersion(bom, "tools.jackson", artifact, "${jackson3.version}"), artifact + " is not explicitly managed for Jackson 3 by the published BOM");
    }
    check(!stdio.contains("<version>${jackson"), "STDIO transport overrides BOM-managed annotations");
    check(!http.contains("<version>${jackson"), "HTTP transport overrides BOM-managed annotations");
    check(scan.contains("aquasec/trivy:0.72.0"), "Trivy container is not pinned");
    check(scan.contains("--severity HIGH,CRITICAL"), "surface scan does not cover HIGH and CRITICAL findings");
    check(scan.contains("*-cyclonedx.json"), "surface scan is not exhaustive over staged SBOMs");
    for (String workflow : List.of("container-images.yml", "mcp-image-release.yml")) {
      String yaml = Files.readString(Path.of(".github", "workflows", workflow));
      check(yaml.contains("severity: 'HIGH,CRITICAL'"), workflow + " does not cover HIGH findings");
    }
    System.out.println("Jackson dependency policy tests passed: " + assertions);
  }

  private static String property(String pom, String name) {
    var matcher = Pattern.compile("<" + Pattern.quote(name) + ">\\s*([^<]+?)\\s*</" + Pattern.quote(name) + ">").matcher(pom);
    check(matcher.find(), "missing property " + name);
    return matcher.group(1);
  }

  private static boolean managedWithVersion(String pom, String artifact, String version) {
    return managedWithVersion(pom, "com.fasterxml.jackson", artifact, version);
  }

  private static boolean managedWithVersion(String pom, String groupPrefix, String artifact, String version) {
    String dependency = "(?s)<dependency>\\s*<groupId>" + Pattern.quote(groupPrefix) + "\\.(?:core|dataformat)</groupId>\\s*<artifactId>"
        + Pattern.quote(artifact) + "</artifactId>\\s*<version>" + Pattern.quote(version) + "</version>\\s*</dependency>";
    return Pattern.compile(dependency).matcher(pom).find();
  }

  private static void check(boolean condition, String message) {
    assertions++;
    if (!condition) throw new AssertionError(message);
  }
}

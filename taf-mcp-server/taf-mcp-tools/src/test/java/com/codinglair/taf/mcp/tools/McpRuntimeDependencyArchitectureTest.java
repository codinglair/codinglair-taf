package com.codinglair.taf.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@DisplayName("Runtime and MCP dependency architecture")
class McpRuntimeDependencyArchitectureTest {
  private static final String PROJECT_GROUP = "com.codinglair.taf";

  @Nested
  @DisplayName("Runtime independence")
  class RuntimeIndependence {
    @Test
    @DisplayName("Runtime modules declare no MCP dependencies")
    void runtimeModulesDeclareNoMcpDependencies() throws Exception {
      Path runtime = repositoryRoot().resolve("codinglair-taf-runtime");

      try (Stream<Path> files = Files.walk(runtime)) {
        List<String> violations =
            files
                .filter(path -> path.getFileName().toString().equals("pom.xml"))
                .flatMap(McpRuntimeDependencyArchitectureTest::declaredDependencies)
                .filter(Dependency::isMcp)
                .map(Dependency::description)
                .sorted()
                .toList();

        assertThat(violations).as("Runtime POM dependencies on MCP").isEmpty();
      }
    }

    @Test
    @DisplayName("Runtime production sources import no MCP packages")
    void runtimeProductionSourcesImportNoMcpPackages() throws IOException {
      Path runtime = repositoryRoot().resolve("codinglair-taf-runtime");

      try (Stream<Path> files = Files.walk(runtime)) {
        List<String> violations =
            files
                .filter(path -> path.toString().replace('\\', '/').contains("/src/main/java/"))
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .filter(McpRuntimeDependencyArchitectureTest::importsMcp)
                .map(runtime::relativize)
                .map(Path::toString)
                .sorted()
                .toList();

        assertThat(violations).as("Runtime production imports from MCP").isEmpty();
      }
    }
  }

  @Test
  @DisplayName("MCP tools declare no transport dependency")
  void mcpToolsDeclareNoTransportDependency() {
    Path pom = repositoryRoot().resolve("taf-mcp-server/taf-mcp-tools/pom.xml");

    assertThat(declaredDependencies(pom).filter(Dependency::isTransport).toList())
        .as("transport dependencies of the shared MCP tools module")
        .isEmpty();
  }

  private static Stream<Dependency> declaredDependencies(Path pom) {
    try {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      var document = factory.newDocumentBuilder().parse(pom.toFile());
      var expression =
          XPathFactory.newInstance().newXPath().compile("/project/dependencies/dependency");
      var nodes = (NodeList) expression.evaluate(document, XPathConstants.NODESET);
      var dependencies = new java.util.ArrayList<Dependency>(nodes.getLength());
      for (int index = 0; index < nodes.getLength(); index++) {
        var dependency = (Element) nodes.item(index);
        dependencies.add(
            new Dependency(
                pom, childText(dependency, "groupId"), childText(dependency, "artifactId")));
      }
      return dependencies.stream();
    } catch (Exception failure) {
      throw new IllegalStateException("Cannot inspect Maven dependencies in " + pom, failure);
    }
  }

  private static String childText(Element parent, String name) {
    return parent.getElementsByTagName(name).item(0).getTextContent().strip();
  }

  private static boolean importsMcp(Path source) {
    try {
      return Files.readString(source, StandardCharsets.UTF_8)
          .contains("import com.codinglair.taf.mcp.");
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot inspect source " + source, failure);
    }
  }

  private static Path repositoryRoot() {
    Path current = Path.of("").toAbsolutePath().normalize();
    while (current != null) {
      if (Files.isDirectory(current.resolve("codinglair-taf-runtime"))
          && Files.isDirectory(current.resolve("taf-mcp-server"))) return current;
      current = current.getParent();
    }
    throw new IllegalStateException("Cannot locate repository root");
  }

  private record Dependency(Path pom, String groupId, String artifactId) {
    boolean isMcp() {
      return PROJECT_GROUP.equals(groupId)
          && (artifactId.startsWith("taf-mcp-") || artifactId.equals("codinglair-taf-mcp"));
    }

    boolean isTransport() {
      return PROJECT_GROUP.equals(groupId) && artifactId.startsWith("taf-mcp-transport-");
    }

    String description() {
      return pom + ": " + groupId + ":" + artifactId;
    }
  }
}

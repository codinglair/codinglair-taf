package com.codinglair.taf.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.StreamSupport;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

@DisplayName("Starter capability manifest contract")
class StarterCapabilityManifestTest {
  private static final Set<String> PROVIDER_MODULES =
      Set.of(
          "taf-messaging-kafka",
          "taf-messaging-rabbitmq",
          "taf-messaging-jms",
          "taf-messaging-aws");
  private static final Path ROOT = repositoryRoot();
  private static final Path MANIFEST =
      ROOT.resolve("docs/reference/starter-capability-manifest-v1.json");
  private static final Path BOM = ROOT.resolve("codinglair-taf-bom/pom.xml");
  private static final ObjectMapper JSON = new ObjectMapper();

  @Nested
  @DisplayName("Authoritative mappings")
  class AuthoritativeMappings {
    @Test
    @DisplayName("maps every capability and provider to one unique starter")
    void mapsEveryCapabilityAndProviderToOneUniqueStarter() throws IOException {
      JsonNode manifest = manifest();

      assertThat(manifest.path("schemaVersion").asText()).isEqualTo("1.0");
      assertThat(manifest.path("groupId").asText()).isEqualTo("com.codinglair.taf");
      assertThat(manifest.path("packaging").asText()).isEqualTo("pom");
      assertThat(textValues(manifest.path("capabilities"), "id"))
          .containsExactlyInAnyOrder("WEB", "API", "DATABASE", "MESSAGING", "MOBILE");
      assertThat(textValues(manifest.path("messagingProviders"), "id"))
          .containsExactlyInAnyOrder("KAFKA", "RABBITMQ", "JMS", "AWS");

      Set<String> starters = new HashSet<>();
      starters.addAll(textValues(manifest.path("capabilities"), "starter"));
      starters.addAll(textValues(manifest.path("messagingProviders"), "starter"));
      assertThat(starters).hasSize(9).allMatch(name -> name.startsWith("codinglair-taf-starter-"));

      StreamSupport.stream(manifest.path("capabilities").spliterator(), false)
          .forEach(capability -> assertThat(capability.path("requiredConfiguration")).isNotEmpty());
      StreamSupport.stream(manifest.path("messagingProviders").spliterator(), false)
          .forEach(provider -> assertThat(provider.path("requiredConfiguration")).isNotEmpty());
      assertThat(textValues(manifest.path("sharedFoundation")))
          .contains("taf-secrets-api", "taf-secrets-local")
          .doesNotHaveDuplicates();
      assertThat(manifest.path("activation").path("singleProviderSelection").asText())
          .contains("taf.secrets.provider");
      assertThat(manifest.path("activation").path("multipleProviderRouting").asText())
          .contains("taf.secrets.routing");
      assertThat(manifest.path("activation").path("documentedLocalProfile").asText())
          .contains("taf-local", "env only");
    }
  }

  @Nested
  @DisplayName("Messaging isolation")
  class MessagingIsolation {
    @Test
    @DisplayName(
        "keeps the generic starter provider neutral and composes every provider through it")
    void keepsGenericStarterProviderNeutral() throws IOException {
      JsonNode manifest = manifest();
      JsonNode messaging =
          StreamSupport.stream(manifest.path("capabilities").spliterator(), false)
              .filter(capability -> capability.path("id").asText().equals("MESSAGING"))
              .findFirst()
              .orElseThrow();

      assertThat(textValues(messaging.path("implementationModules")))
          .containsExactly("taf-messaging-core");
      assertThat(textValues(messaging.path("implementationModules")))
          .doesNotContainAnyElementsOf(PROVIDER_MODULES);
      StreamSupport.stream(manifest.path("messagingProviders").spliterator(), false)
          .forEach(
              provider -> {
                assertThat(provider.path("genericStarter").asText())
                    .isEqualTo("codinglair-taf-starter-messaging");
                assertThat(provider.path("implementationModule").asText()).isIn(PROVIDER_MODULES);
              });
    }
  }

  @Nested
  @DisplayName("BOM and architecture boundaries")
  class BomAndArchitectureBoundaries {
    @Test
    @DisplayName("materializes every non-messaging starter as its exact manifest graph")
    void materializesEveryNonMessagingStarterFromTheManifest() throws Exception {
      JsonNode manifest = manifest();
      Set<String> sharedFoundation = textValues(manifest.path("sharedFoundation"));

      for (JsonNode capability : manifest.path("capabilities")) {
        if (capability.path("id").asText().equals("MESSAGING")) continue;

        String starter = capability.path("starter").asText();
        Path starterPom = ROOT.resolve(starter).resolve("pom.xml");
        Set<String> expectedDependencies = new HashSet<>(sharedFoundation);
        expectedDependencies.addAll(textValues(capability.path("implementationModules")));

        assertThat(starterPom).exists();
        assertThat(projectPackaging(starterPom)).isEqualTo("pom");
        assertThat(directDependencyArtifacts(starterPom))
            .as("%s must contain only its manifest-declared graph", starter)
            .containsExactlyInAnyOrderElementsOf(expectedDependencies);
        assertThat(textValues(capability.path("supportedExclusions")))
            .as("required top-level infrastructure must not be advertised as safely excludable")
            .isEmpty();
      }
    }

    @Test
    @DisplayName("aligns every public starter and supported direct module in dependency management")
    void alignsEveryPublicArtifactInDependencyManagement() throws Exception {
      JsonNode manifest = manifest();
      Set<String> managedArtifacts = managedBomArtifacts();
      Set<String> required = new HashSet<>(textValues(manifest.path("sharedFoundation")));
      required.addAll(textValues(manifest.path("capabilities"), "starter"));
      required.addAll(textValues(manifest.path("messagingProviders"), "starter"));
      StreamSupport.stream(manifest.path("capabilities").spliterator(), false)
          .forEach(
              capability -> required.addAll(textValues(capability.path("implementationModules"))));
      required.addAll(PROVIDER_MODULES);

      assertThat(managedArtifacts).containsAll(required);
      assertThat(managedArtifacts).contains("codinglair-taf-bom");
    }

    @Test
    @DisplayName("prevents starter graphs from crossing forbidden product boundaries")
    void preventsStarterGraphsFromCrossingForbiddenBoundaries() throws IOException {
      JsonNode manifest = manifest();
      Set<String> forbidden = textValues(manifest.path("forbiddenDependencyTargets"));
      Set<String> graphArtifacts = new HashSet<>(textValues(manifest.path("sharedFoundation")));
      StreamSupport.stream(manifest.path("capabilities").spliterator(), false)
          .forEach(
              capability ->
                  graphArtifacts.addAll(textValues(capability.path("implementationModules"))));
      graphArtifacts.addAll(
          textValues(manifest.path("messagingProviders"), "implementationModule"));

      assertThat(graphArtifacts)
          .allMatch(
              artifact -> forbidden.stream().noneMatch(target -> artifact.contains(target)),
              "starter graph artifacts must not target examples, MCP, control-plane, consumer, or proprietary projects");
    }
  }

  private static JsonNode manifest() throws IOException {
    return JSON.readTree(MANIFEST.toFile());
  }

  private static Set<String> textValues(JsonNode array) {
    Set<String> values = new HashSet<>();
    array.forEach(value -> values.add(value.asText()));
    return values;
  }

  private static Set<String> textValues(JsonNode array, String field) {
    Set<String> values = new HashSet<>();
    array.forEach(value -> values.add(value.path(field).asText()));
    return values;
  }

  private static Set<String> managedBomArtifacts() throws Exception {
    var factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    var document = factory.newDocumentBuilder().parse(BOM.toFile());
    Set<String> artifacts = new HashSet<>();
    var dependencyManagement = document.getElementsByTagName("dependencyManagement").item(0);
    var descendants = dependencyManagement.getChildNodes();
    collectArtifactIds(descendants, artifacts);
    artifacts.add("codinglair-taf-bom");
    return artifacts;
  }

  private static String projectPackaging(Path pom) throws Exception {
    var document = secureDocumentBuilderFactory().newDocumentBuilder().parse(pom.toFile());
    var packaging = document.getDocumentElement().getElementsByTagName("packaging");
    return packaging.getLength() == 0 ? "jar" : packaging.item(0).getTextContent().trim();
  }

  private static Set<String> directDependencyArtifacts(Path pom) throws Exception {
    var document = secureDocumentBuilderFactory().newDocumentBuilder().parse(pom.toFile());
    Element dependencies = null;
    var projectChildren = document.getDocumentElement().getChildNodes();
    for (int index = 0; index < projectChildren.getLength(); index++) {
      Node node = projectChildren.item(index);
      if (node instanceof Element element && element.getTagName().equals("dependencies")) {
        dependencies = element;
        break;
      }
    }
    if (dependencies == null) return Set.of();
    Set<String> artifacts = new HashSet<>();
    for (int index = 0; index < dependencies.getChildNodes().getLength(); index++) {
      Node node = dependencies.getChildNodes().item(index);
      if (!(node instanceof Element dependency) || !dependency.getTagName().equals("dependency"))
        continue;
      var artifactIds = dependency.getElementsByTagName("artifactId");
      artifacts.add(artifactIds.item(0).getTextContent().trim());
    }
    return artifacts;
  }

  private static DocumentBuilderFactory secureDocumentBuilderFactory() throws Exception {
    var factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    return factory;
  }

  private static void collectArtifactIds(org.w3c.dom.NodeList nodes, Set<String> artifacts) {
    for (int index = 0; index < nodes.getLength(); index++) {
      Node node = nodes.item(index);
      if (node instanceof Element element && element.getTagName().equals("artifactId")) {
        artifacts.add(element.getTextContent().trim());
      }
      collectArtifactIds(node.getChildNodes(), artifacts);
    }
  }

  private static Path repositoryRoot() {
    Path candidate = Path.of("").toAbsolutePath().normalize();
    while (candidate != null) {
      if (Files.exists(candidate.resolve("codinglair-taf-bom/pom.xml"))) return candidate;
      candidate = candidate.getParent();
    }
    throw new IllegalStateException(
        "Cannot locate repository root from " + Path.of("").toAbsolutePath());
  }
}

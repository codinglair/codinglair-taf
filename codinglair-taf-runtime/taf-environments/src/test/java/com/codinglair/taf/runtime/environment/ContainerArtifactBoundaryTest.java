package com.codinglair.taf.runtime.environment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ContainerArtifactBoundaryTest {
  @Test
  void runtimeParentDisablesTestJarPublicationForAllRuntimeModules() throws Exception {
    String runtimeParentPom = Files.readString(Path.of("..", "pom.xml"));
    assertThat(runtimeParentPom)
        .contains("<artifactId>maven-jar-plugin</artifactId>")
        .contains("<id>default</id>")
        .contains("<phase>none</phase>");

    String modulePom = Files.readString(Path.of("pom.xml"));
    assertThat(modulePom).doesNotContain("<goal>test-jar</goal>");
  }

  @Test
  void springMetadataIsPackagedWithoutTestFixtures() throws Exception {
    Path classes = Path.of("target", "classes");
    assertThat(
            classes.resolve(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"))
        .exists();
    assertThat(classes.resolve("META-INF/additional-spring-configuration-metadata.json")).exists();
    try (var files = Files.walk(classes)) {
      assertThat(files.filter(Files::isRegularFile).map(Path::toString))
          .noneMatch(
              name ->
                  name.contains("TestResource")
                      || name.contains("FakeEnvironmentProvider")
                      || name.contains("consumer-config"));
    }
  }
}

package com.codinglair.taf.conformance.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Effective consumer build conformance")
class ConsumerBuildConformanceTest {
  @TempDir Path temp;

  @Nested
  @DisplayName("Provider and dependency validation")
  class ProviderValidation {
    @Test
    @DisplayName("Accepts an explicit TestNG provider and resolved TAF runner")
    void acceptsSupportedBuild() throws Exception {
      MavenProject project = project(true, false);
      descriptor("1.2");

      assertThat(ConsumerBuildConformance.validate(project, temp)).isEmpty();
    }

    @Test
    @DisplayName("Rejects inherited provider substitution and a resolved JUnit engine")
    void rejectsMixedResolvedBuild() throws Exception {
      MavenProject project = project(false, true);
      descriptor("1.2");

      assertThat(ConsumerBuildConformance.validate(project, temp))
          .anyMatch(value -> value.startsWith("provider.testng"))
          .anyMatch(value -> value.startsWith("engine.junit"));
    }

    @Test
    @DisplayName("Requires migration of an older governed descriptor")
    void rejectsOldDescriptor() throws Exception {
      MavenProject project = project(true, false);
      descriptor("1.1");

      assertThat(ConsumerBuildConformance.validate(project, temp))
          .anyMatch(value -> value.startsWith("descriptor.version"));
    }
  }

  private MavenProject project(boolean provider, boolean junit) {
    var project = new MavenProject();
    var build = new Build();
    var surefire = new Plugin();
    surefire.setGroupId("org.apache.maven.plugins");
    surefire.setArtifactId("maven-surefire-plugin");
    if (provider) {
      var dependency = new Dependency();
      dependency.setGroupId("org.apache.maven.surefire");
      dependency.setArtifactId("surefire-testng");
      surefire.addDependency(dependency);
    }
    build.addPlugin(surefire);
    project.setBuild(build);
    var artifacts = new java.util.HashSet<org.apache.maven.artifact.Artifact>();
    artifacts.add(artifact("com.codinglair.taf", "codinglair-taf-runner-testng"));
    if (junit) artifacts.add(artifact("org.junit.jupiter", "junit-jupiter-engine"));
    project.setArtifacts(Set.copyOf(artifacts));
    return project;
  }

  private static org.apache.maven.artifact.Artifact artifact(String group, String name) {
    return new DefaultArtifact(
        group, name, "1", "test", "jar", null, new DefaultArtifactHandler("jar"));
  }

  private void descriptor(String version) throws Exception {
    Path file = temp.resolve("src/test/resources/taf-project.json");
    Files.createDirectories(file.getParent());
    Files.writeString(
        file,
        """
        {"schemaVersion":"%s","project":{"name":"consumer","type":"standalone-test-automation","basePackage":"com.example","expectedBuildRoot":".","sutInteraction":"external"},"runtime":{"version":"1"},"capabilities":["files"],"runners":["testng"],"testDefinitions":{"provider":"file-csv","authoritative":true},"reporting":{"adapter":"allure"}}
        """
            .formatted(version));
  }
}

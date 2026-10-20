package com.codinglair.taf.conformance.maven;

import com.codinglair.taf.conformance.BlueprintDescriptor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.project.MavenProject;

final class ConsumerBuildConformance {
  private static final String SUREFIRE = "org.apache.maven.plugins:maven-surefire-plugin";
  private static final String TESTNG_PROVIDER = "org.apache.maven.surefire:surefire-testng";
  private static final String TESTNG_RUNNER = "com.codinglair.taf:codinglair-taf-runner-testng";
  private static final String CUCUMBER_RUNNER = "com.codinglair.taf:codinglair-taf-runner-cucumber";
  private static final Set<String> JUNIT_GROUPS = Set.of("junit", "org.junit", "org.junit.jupiter");
  private static final ObjectMapper JSON =
      new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private ConsumerBuildConformance() {}

  static List<String> validate(MavenProject project, Path root) {
    return validate(project, null, null, root);
  }

  static List<String> validate(
      MavenProject project, MavenSession session, MavenPluginManager pluginManager, Path root) {
    var violations = new ArrayList<String>();
    BlueprintDescriptor descriptor = descriptor(root, violations);
    Plugin surefire = plugin(project, SUREFIRE);
    if (surefire == null) {
      violations.add("provider.missing: declare maven-surefire-plugin in the effective build");
    } else if (surefire.getDependencies().stream()
        .noneMatch(ConsumerBuildConformance::testNgProvider)) {
      violations.add(
          "provider.testng: declare org.apache.maven.surefire:surefire-testng as an explicit "
              + "maven-surefire-plugin dependency");
    } else if (session != null && !resolvedProvider(project, session, pluginManager, surefire)) {
      violations.add(
          "provider.resolved: resolved Surefire plugin classpath lacks " + TESTNG_PROVIDER);
    }
    Set<Artifact> artifacts = project.getArtifacts();
    if (artifacts.stream().noneMatch(artifact -> TESTNG_RUNNER.equals(coordinate(artifact)))) {
      violations.add("integration.testng: resolved test classpath lacks " + TESTNG_RUNNER);
    }
    artifacts.stream()
        .filter(artifact -> JUNIT_GROUPS.contains(artifact.getGroupId()))
        .forEach(
            artifact ->
                violations.add(
                    "engine.junit: executable JUnit artifact is unsupported in a governed consumer: "
                        + coordinate(artifact)));
    if (descriptor != null) {
      if (!"1.2".equals(descriptor.schemaVersion())) {
        violations.add(
            "descriptor.version: governed provider validation requires schemaVersion 1.2; migrate "
                + "1.0/1.1 by retaining the standalone fields and adding the mandatory conformance plugin");
      }
      if (descriptor.runners() != null
          && descriptor.runners().contains("cucumber-testng")
          && artifacts.stream()
              .noneMatch(artifact -> CUCUMBER_RUNNER.equals(coordinate(artifact)))) {
        violations.add("integration.cucumber: resolved test classpath lacks " + CUCUMBER_RUNNER);
      }
    }
    return List.copyOf(violations);
  }

  private static boolean resolvedProvider(
      MavenProject project,
      MavenSession session,
      MavenPluginManager pluginManager,
      Plugin surefire) {
    if (pluginManager == null) return false;
    try {
      var descriptor =
          pluginManager.getPluginDescriptor(
              surefire, project.getRemotePluginRepositories(), session.getRepositorySession());
      pluginManager.setupPluginRealm(descriptor, session, null, List.of(), null);
      return descriptor.getArtifacts().stream()
          .anyMatch(artifact -> TESTNG_PROVIDER.equals(coordinate(artifact)));
    } catch (Exception failure) {
      return false;
    }
  }

  private static BlueprintDescriptor descriptor(Path root, List<String> violations) {
    Path file = root.resolve("src/test/resources/taf-project.json");
    if (!Files.isRegularFile(file)) {
      violations.add(
          "descriptor.missing: a governed consumer must provide src/test/resources/taf-project.json");
      return null;
    }
    try {
      return JSON.readValue(file.toFile(), BlueprintDescriptor.class);
    } catch (IOException failure) {
      violations.add("descriptor.invalid: taf-project.json is malformed or has unknown fields");
      return null;
    }
  }

  private static Plugin plugin(MavenProject project, String coordinate) {
    return project.getBuildPlugins().stream()
        .filter(candidate -> coordinate.equals(candidate.getKey()))
        .findFirst()
        .orElse(null);
  }

  private static boolean testNgProvider(Dependency dependency) {
    return TESTNG_PROVIDER.equals(dependency.getGroupId() + ":" + dependency.getArtifactId());
  }

  private static String coordinate(Artifact artifact) {
    return artifact.getGroupId() + ":" + artifact.getArtifactId();
  }
}

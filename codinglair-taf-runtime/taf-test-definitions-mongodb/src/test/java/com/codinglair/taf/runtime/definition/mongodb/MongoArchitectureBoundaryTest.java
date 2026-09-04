package com.codinglair.taf.runtime.definition.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MongoDB definition provider architecture boundaries")
class MongoArchitectureBoundaryTest {
  @Test
  @DisplayName(
      "keeps MongoDB out of the neutral SPI and SUT database capability out of the adapter")
  void preservesOptionalContextPlaneIsolation() throws Exception {
    String adapterPom = Files.readString(Path.of("pom.xml"));
    String neutralPom = Files.readString(Path.of("..", "taf-test-definitions", "pom.xml"));

    assertThat(adapterPom)
        .doesNotContain("<artifactId>taf-database</artifactId>")
        .doesNotContain("codinglair-taf-mcp")
        .doesNotContain("codinglair-taf-quality-intelligence");
    assertThat(neutralPom)
        .doesNotContain("mongodb-driver")
        .doesNotContain("spring-boot-autoconfigure");
  }
}

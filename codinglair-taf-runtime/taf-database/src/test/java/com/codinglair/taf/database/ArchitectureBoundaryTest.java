package com.codinglair.taf.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Database capability architecture boundary")
class ArchitectureBoundaryTest {
  @Test
  @DisplayName("does not depend on MCP proprietary Allure Flyway or MongoDB code")
  void preservesBoundaries() throws Exception {
    Path source = Path.of("src/main/java");
    String content;
    try (var files = Files.walk(source)) {
      content =
          files
              .filter(path -> path.toString().endsWith(".java"))
              .map(
                  path -> {
                    try {
                      return Files.readString(path);
                    } catch (Exception failure) {
                      throw new RuntimeException(failure);
                    }
                  })
              .reduce("", String::concat);
    }
    assertThat(content)
        .doesNotContain(
            "taf.mcp",
            "quality.intelligence",
            "io.qameta",
            "org.flywaydb",
            "com.mongodb",
            "MongoClient",
            "DataSource");
  }
}

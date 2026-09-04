package com.codinglair.taf.runtime.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ArchitectureBoundaryTest {
  @Test
  void coreProductionSourcesContainNoOptionalImplementations() throws Exception {
    Path root = Path.of("src/main/java");
    try (var files = Files.walk(root)) {
      var sources =
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
              .toList();
      assertThat(sources)
          .noneMatch(
              source ->
                  source.contains("com.microsoft.playwright")
                      || source.contains("org.testcontainers")
                      || source.contains("io.qameta.allure")
                      || source.contains("PlaywrightControllerAutoConfiguration"));
    }
  }
}

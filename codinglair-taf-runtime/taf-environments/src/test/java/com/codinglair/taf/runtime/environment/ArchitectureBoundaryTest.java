package com.codinglair.taf.runtime.environment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ArchitectureBoundaryTest {
  @Test
  void productionSourcesDoNotReferenceConcreteControllers() throws IOException {
    Path sourceRoot = Path.of("src", "main", "java");
    try (var sources = Files.walk(sourceRoot)) {
      assertThat(
              sources
                  .filter(path -> path.toString().endsWith(".java"))
                  .map(this::readUnchecked)
                  .noneMatch(
                      source ->
                          source.contains("PlaywrightController")
                              || source.contains("RestController")
                              || source.contains("AppiumController")
                              || source.contains("com.microsoft.playwright")
                              || source.contains("io.qameta.allure")))
          .isTrue();
    }
  }

  private String readUnchecked(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException failure) {
      throw new IllegalStateException(failure);
    }
  }
}

package com.codinglair.taf.mobile;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Mobile core architecture boundary")
class MobileArchitectureBoundaryTest {
  @Test
  @DisplayName("contains no Appium Selenium Spring or provisioning implementation imports")
  void remainsDependencyLight() throws Exception {
    try (var paths = Files.walk(Path.of("src/main/java"))) {
      var source =
          paths
              .filter(path -> path.toString().endsWith(".java"))
              .map(
                  path -> {
                    try {
                      return Files.readString(path);
                    } catch (Exception failure) {
                      throw new IllegalStateException(failure);
                    }
                  })
              .toList();
      assertThat(source)
          .noneMatch(
              value ->
                  value.contains("io.appium")
                      || value.contains("org.openqa.selenium")
                      || value.contains("org.springframework")
                      || value.contains("new ProcessBuilder"));
    }
  }
}

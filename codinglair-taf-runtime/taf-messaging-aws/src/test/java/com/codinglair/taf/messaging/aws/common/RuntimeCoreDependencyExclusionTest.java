package com.codinglair.taf.messaging.aws.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RuntimeCoreDependencyExclusionTest {
  @Test
  void runtimeCorePomContainsNoAwsOrLocalstackDependency() throws Exception {
    String pom = Files.readString(Path.of("..", "codinglair-taf-runtime-core", "pom.xml"));
    assertThat(pom).doesNotContain("software.amazon.awssdk", "localstack");
  }
}

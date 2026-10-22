package com.codinglair.taf.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenWrapperScaffoldCompilerTest {
  @TempDir Path workspace;

  @Test
  void failsSafelyWhenTheGeneratedWrapperIsMissing() {
    var compiler =
        new MavenWrapperScaffoldCompiler(Duration.ofSeconds(1), System.getProperty("os.name"));

    var result = compiler.compile(workspace);

    assertThat(result.successful()).isFalse();
    assertThat(result.summary()).isEqualTo("generated Maven wrapper is unavailable");
  }
}

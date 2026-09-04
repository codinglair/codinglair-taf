package com.codinglair.taf.runtime.file;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionConfigurer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("File validation auto-configuration")
class FileValidationAutoConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  TafRuntimeAutoConfiguration.class, FileValidationAutoConfiguration.class));

  @Test
  @DisplayName("backs off while disabled")
  void disabled() {
    runner.run(context -> assertThat(context).doesNotHaveBean(FileValidationProperties.class));
  }

  @Test
  @DisplayName("registers a session configurer when enabled")
  void enabled() {
    runner
        .withPropertyValues("taf.file.enabled=true", "taf.file.sandboxes.default.root=target")
        .run(
            context ->
                assertThat(context)
                    .hasSingleBean(FileValidationProperties.class)
                    .getBeans(TestSessionConfigurer.class)
                    .containsKey("fileValidationSessionConfigurer"));
  }
}

package com.codinglair.taf.runtime.file;

import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionConfigurer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(TafRuntimeAutoConfiguration.class)
@ConditionalOnClass(FileController.class)
@ConditionalOnProperty(prefix = "taf.file", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(FileValidationProperties.class)
public class FileValidationAutoConfiguration {
  @Bean
  TestSessionConfigurer fileValidationSessionConfigurer(FileValidationProperties properties) {
    return session ->
        properties
            .getSandboxes()
            .forEach(
                (name, sandbox) -> {
                  if (sandbox.getRoot() == null)
                    throw new IllegalStateException(
                        "taf.file.sandboxes." + name + ".root must be configured");
                  session
                      .getControllerRegistry()
                      .register(
                          FileController.class,
                          name,
                          new DefaultFileController(
                              name, sandbox.getRoot(), sandbox.getMaximumSize()));
                });
  }
}

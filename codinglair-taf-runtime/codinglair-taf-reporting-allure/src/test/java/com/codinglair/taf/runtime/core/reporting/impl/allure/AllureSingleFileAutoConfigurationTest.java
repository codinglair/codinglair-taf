package com.codinglair.taf.runtime.core.reporting.impl.allure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AllureSingleFileAutoConfigurationTest {
  private final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(AllureSingleFileAutoConfiguration.class));

  @Test
  void documentedDefaultsBindWithoutEnablingPublication() {
    context.run(
        application -> {
          assertThat(application).hasNotFailed();
          assertThat(application).doesNotHaveBean(AllureSingleFilePublisher.class);
          AllureSingleFileProperties properties =
              application.getBean(AllureSingleFileProperties.class);
          assertThat(properties.isEnabled()).isFalse();
          assertThat(properties.getOutputDirectory()).isEqualTo(Path.of("target", "taf-reports"));
          assertThat(properties.getResultsDirectory())
              .isEqualTo(Path.of("target", "allure-results"));
          assertThat(properties.getReportName()).isEqualTo("TAF Test Report");
          assertThat(properties.getTimestampPattern()).isEqualTo("yyyyMMddHHmm");
          assertThat(properties.getExecutable()).isEqualTo("allure");
        });
  }

  @Test
  void enabledConfigurationBindsCustomValuesAndCreatesPublisher() {
    context
        .withPropertyValues(
            "taf.reporting.allure.single-file.enabled=true",
            "taf.reporting.allure.single-file.output-directory=build/reports",
            "taf.reporting.allure.single-file.results-directory=build/results",
            "taf.reporting.allure.single-file.report-name=Functional Report",
            "taf.reporting.allure.single-file.timestamp-pattern=yyyy-MM-dd_HHmmss",
            "taf.reporting.allure.single-file.executable=/opt/allure/bin/allure")
        .run(
            application -> {
              assertThat(application).hasNotFailed();
              assertThat(application).hasSingleBean(AllureSingleFilePublisher.class);
              AllureSingleFileProperties properties =
                  application.getBean(AllureSingleFileProperties.class);
              assertThat(properties.getOutputDirectory()).isEqualTo(Path.of("build/reports"));
              assertThat(properties.getReportName()).isEqualTo("Functional Report");
              assertThat(properties.getTimestampPattern()).isEqualTo("yyyy-MM-dd_HHmmss");
            });
  }

  @Test
  void invalidTimestampFailsDuringContextPreflight() {
    context
        .withPropertyValues(
            "taf.reporting.allure.single-file.enabled=true",
            "taf.reporting.allure.single-file.timestamp-pattern=yyyy/MM/dd")
        .run(application -> assertThat(application).hasFailed());
  }
}

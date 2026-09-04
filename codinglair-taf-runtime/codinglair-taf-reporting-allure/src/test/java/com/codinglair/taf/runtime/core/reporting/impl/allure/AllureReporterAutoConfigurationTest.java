package com.codinglair.taf.runtime.core.reporting.impl.allure;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestReporter;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestStep;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AllureReporterAutoConfigurationTest {
  private final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(AllureReporterAutoConfiguration.class));

  @Test
  void contributesAllureToSpringManagedReporting() {
    context.run(
        application -> {
          assertThat(application).hasNotFailed();
          assertThat(application).hasSingleBean(TestReporter.class);
          assertThat(application.getBean(TestReporter.class)).isInstanceOf(AllureReporter.class);
        });
  }

  @Test
  void backsOffForAConsumerReporter() {
    context
        .withBean(TestReporter.class, RecordingReporter::new)
        .run(
            application -> {
              assertThat(application).hasNotFailed();
              assertThat(application).hasSingleBean(TestReporter.class);
              assertThat(application.getBean(TestReporter.class))
                  .isInstanceOf(RecordingReporter.class);
            });
  }

  private static final class RecordingReporter implements TestReporter {
    @Override
    public void beginTest(TafTest test) {}

    @Override
    public void endTest(TafTest test) {}

    @Override
    public void reportStep(TestStep step) {}

    @Override
    public void reportArtifact(TestArtifact artifact) {}

    @Override
    public String getName() {
      return "recording";
    }
  }
}

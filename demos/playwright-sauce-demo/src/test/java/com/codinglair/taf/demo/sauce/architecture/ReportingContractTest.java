package com.codinglair.taf.demo.sauce.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.codinglair.taf.demo.sauce.page.LoginPage;
import com.codinglair.taf.demo.sauce.service.LoginWorkflow;
import com.codinglair.taf.runtime.core.reporting.annotation.PageAction;
import com.codinglair.taf.runtime.core.reporting.annotation.Workflow;
import com.codinglair.taf.runtime.core.reporting.impl.allure.AllureSingleFileAutoConfiguration;
import com.codinglair.taf.runtime.core.reporting.impl.allure.AllureSingleFileProperties;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

class ReportingContractTest {
  @org.junit.jupiter.api.Test
  void meaningfulActionsUseNeutralTafAnnotations() throws Exception {
    assertNotNull(
        LoginPage.class.getMethod("enterUsername", String.class).getAnnotation(PageAction.class));
    assertNotNull(
        LoginWorkflow.class
            .getMethod("authenticate", String.class, String.class, String.class)
            .getAnnotation(Workflow.class));
  }

  @org.junit.jupiter.api.Test
  void standardAllureResultsOverrideAlsoConfiguresSingleFilePublisher() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AllureSingleFileAutoConfiguration.class))
        .withInitializer(
            context -> {
              try {
                var sources =
                    new YamlPropertySourceLoader()
                        .load("sauce-demo", new ClassPathResource("application.yaml"));
                sources.forEach(context.getEnvironment().getPropertySources()::addLast);
              } catch (java.io.IOException failure) {
                throw new IllegalStateException("Cannot load Sauce Demo configuration", failure);
              }
            })
        .withPropertyValues(
            "allure.results.directory=target/allure-results-functional",
            "taf.reporting.allure.single-file.enabled=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(AllureSingleFileProperties.class);
              AllureSingleFileProperties properties =
                  Binder.get(context.getEnvironment())
                      .bind(
                          "taf.reporting.allure.single-file",
                          Bindable.of(AllureSingleFileProperties.class))
                      .orElseThrow(
                          () -> new AssertionError("Allure single-file properties were not bound"));
              assertThat(properties.getResultsDirectory())
                  .isEqualTo(Path.of("target/allure-results-functional"));
            });
  }
}

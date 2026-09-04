package com.codinglair.taf.runtime.core.reporting.impl.allure;

import com.codinglair.taf.runtime.core.reporting.abstraction.TestReporter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Connects the optional Allure adapter to Spring-managed TAF reporting lifecycles. */
@AutoConfiguration
public class AllureReporterAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(TestReporter.class)
  TestReporter allureReporter() {
    return new AllureReporter();
  }
}

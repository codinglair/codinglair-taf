package com.codinglair.taf.runtime.core.reporting.impl.allure;

import java.time.Clock;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Adapter-local Spring Boot composition for optional single-file report publication. */
@AutoConfiguration
@EnableConfigurationProperties(AllureSingleFileProperties.class)
public class AllureSingleFileAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  Clock allureSingleFileClock() {
    return Clock.systemDefaultZone();
  }

  @Bean
  @ConditionalOnMissingBean(SingleFileReportGenerator.class)
  SingleFileReportGenerator allureSingleFileReportGenerator() {
    return new AllureCliSingleFileReportGenerator();
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "taf.reporting.allure.single-file",
      name = "enabled",
      havingValue = "true")
  AllureSingleFilePublisher allureSingleFilePublisher(
      AllureSingleFileProperties properties, SingleFileReportGenerator generator, Clock clock) {
    return new AllureSingleFilePublisher(properties, generator, clock);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "taf.reporting.allure.single-file",
      name = "enabled",
      havingValue = "true")
  AllureSingleFileRegistration allureSingleFileRegistration(AllureSingleFilePublisher publisher) {
    return new AllureSingleFileRegistration(publisher);
  }

  static final class AllureSingleFileRegistration implements DisposableBean {
    private final AllureSingleFilePublisher publisher;

    private AllureSingleFileRegistration(AllureSingleFilePublisher publisher) {
      this.publisher = publisher;
      AllureSingleFileRunCoordinator.register(publisher);
    }

    @Override
    public void destroy() {
      AllureSingleFileRunCoordinator.unregister(publisher);
    }
  }
}

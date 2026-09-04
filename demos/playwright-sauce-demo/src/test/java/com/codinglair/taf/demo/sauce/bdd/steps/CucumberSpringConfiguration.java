package com.codinglair.taf.demo.sauce.bdd.steps;

import com.codinglair.taf.demo.sauce.SauceDemoApplication;
import com.codinglair.taf.runtime.core.lifecycle.SessionAwareAccessor;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionLifecycle;
import com.codinglair.taf.runtime.core.reporting.CurrentReportingContext;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestReporter;
import com.codinglair.taf.runtime.cucumber.CucumberScenarioSession;
import io.cucumber.spring.CucumberContextConfiguration;
import io.cucumber.spring.ScenarioScope;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.test.context.ActiveProfiles;

@CucumberContextConfiguration
@SpringBootTest(classes = SauceDemoApplication.class)
@Import(CucumberSpringConfiguration.BridgeConfiguration.class)
@ActiveProfiles("cucumber")
public class CucumberSpringConfiguration {
  @Configuration(proxyBeanMethods = false)
  @Profile("cucumber")
  static class BridgeConfiguration {
    @Bean
    @ScenarioScope(proxyMode = ScopedProxyMode.NO)
    CucumberScenarioSession cucumberScenarioSession(
        TestSessionLifecycle lifecycle,
        CurrentReportingContext currentReporting,
        ObjectProvider<TestReporter> reporters) {
      return new CucumberScenarioSession(
          lifecycle, currentReporting, reporters.orderedStream().toList());
    }

    @Bean
    @Primary
    SessionAwareAccessor cucumberSessionAccessor(
        ObjectProvider<CucumberScenarioSession> scenarioSessions) {
      return () -> scenarioSessions.getObject().session();
    }
  }
}

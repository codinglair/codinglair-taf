/*
 * Copyright 2026 Codinglair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.codinglair.taf.runtime.core.autoconfigure;

import com.codinglair.taf.runtime.core.TestSession;
import com.codinglair.taf.runtime.core.controller.ControllerRegistry;
import com.codinglair.taf.runtime.core.lifecycle.CurrentTestSession;
import com.codinglair.taf.runtime.core.lifecycle.DefaultSessionAwareAccessor;
import com.codinglair.taf.runtime.core.lifecycle.SessionAwareAccessor;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionConfigurer;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionLifecycle;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflight;
import com.codinglair.taf.runtime.core.failure.FailureClassificationService;
import com.codinglair.taf.runtime.core.failure.FailureSignatureService;
import com.codinglair.taf.runtime.core.history.DisabledExecutionHistoryRepository;
import com.codinglair.taf.runtime.core.history.ExecutionHistoryRepository;
import com.codinglair.taf.runtime.core.history.FileExecutionHistoryRepository;
import com.codinglair.taf.runtime.core.history.AttemptHistoryService;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflightContributor;
import com.codinglair.taf.runtime.core.reporting.CurrentReportingContext;
import com.codinglair.taf.runtime.core.reporting.ReportingActionInterceptor;
import com.codinglair.taf.runtime.core.reporting.ReportingBeanPostProcessor;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Auto-configuration for TAF Runtime core components.
 *
 * <p>This auto-configuration provides beans for TestSession and ControllerRegistry when no bean of
 * the same type is already defined.
 *
 * <p>Configuration is minimal and dependency-light, following the principle that TAF Runtime should
 * remain usable without MCP or AI.
 *
 * @author Codinglair
 * @since 1.0
 */
@AutoConfiguration
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HistoryProperties.class)
public class TafRuntimeAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public FailureClassificationService failureClassificationService() {
    return new FailureClassificationService();
  }

  @Bean
  @ConditionalOnMissingBean
  public FailureSignatureService failureSignatureService(HistoryProperties properties) {
    return new FailureSignatureService(new com.codinglair.taf.runtime.core.reporting.RedactionPipeline(),
        properties.getSignatureMessageLimit(), properties.getSignatureStackFrameLimit(),
        properties.getSignatureCauseLimit());
  }

  @Bean
  @ConditionalOnMissingBean
  public ExecutionHistoryRepository executionHistoryRepository(HistoryProperties properties) {
    var configuration = properties.configuration();
    return properties.isEnabled()
        ? new FileExecutionHistoryRepository(configuration)
        : new DisabledExecutionHistoryRepository();
  }

  @Bean
  @ConditionalOnMissingBean
  public AttemptHistoryService attemptHistoryService(FailureClassificationService classifications,
      FailureSignatureService signatures, ExecutionHistoryRepository repository,
      HistoryProperties properties) {
    return new AttemptHistoryService(classifications, signatures, repository, properties.configuration(),
        properties.getMinimumSamples(), properties.getProjectId(), properties.getBuildId(),
        properties.getEnvironmentId());
  }

  /**
   * Creates a ControllerRegistry bean when no bean is already defined.
   *
   * @return a new ControllerRegistry instance
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnClass(ControllerRegistry.class)
  public ControllerRegistry controllerRegistry() {
    return new ControllerRegistry();
  }

  /** Creates isolated sessions on demand; mutable sessions are never singleton beans. */
  @Bean
  @ConditionalOnMissingBean
  public TestSessionFactory testSessionFactory(List<TestSessionConfigurer> configurers) {
    List<TestSessionConfigurer> snapshot = List.copyOf(configurers);
    return () -> {
      TestSession session = TestSession.create();
      try {
        snapshot.forEach(configurer -> configurer.configure(session));
        return session;
      } catch (RuntimeException | Error failure) {
        try {
          session.close();
        } catch (Throwable cleanup) {
          failure.addSuppressed(cleanup);
        }
        throw failure;
      }
    };
  }

  @Bean
  @ConditionalOnMissingBean
  public TestSessionLifecycle testSessionLifecycle(TestSessionFactory factory) {
    return new TestSessionLifecycle(factory);
  }

  /** Resolves current invocation state for stable Spring-managed consumer factories. */
  @Bean
  @ConditionalOnMissingBean
  public SessionAwareAccessor sessionAwareAccessor(CurrentTestSession currentSession) {
    return new DefaultSessionAwareAccessor(currentSession);
  }

  /** Aggregates execution checks contributed by selected Runtime capabilities. */
  @Bean
  @ConditionalOnMissingBean
  public ConsumerPreflight consumerPreflight(List<ConsumerPreflightContributor> contributors) {
    return new ConsumerPreflight(contributors);
  }

  @Bean
  @ConditionalOnMissingBean
  public CurrentReportingContext currentReportingContext() {
    return new CurrentReportingContext();
  }

  @Bean
  @ConditionalOnMissingBean
  public ReportingActionInterceptor reportingActionInterceptor(CurrentReportingContext contexts) {
    return new ReportingActionInterceptor(contexts);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnClass(CurrentTestSession.class)
  public CurrentTestSession currentTestSession(TestSessionLifecycle lifecycle) {
    return lifecycle;
  }

  @Bean
  @ConditionalOnMissingBean
  public static ReportingBeanPostProcessor reportingBeanPostProcessor(
      ObjectProvider<ReportingActionInterceptor> interceptor) {
    return new ReportingBeanPostProcessor(interceptor::getObject);
  }

  @Bean
  public TestSessionConfigurer reportingControllerInterception(
      ReportingActionInterceptor interceptor) {
    return session -> session.getControllerRegistry().enableReporting(interceptor);
  }
}

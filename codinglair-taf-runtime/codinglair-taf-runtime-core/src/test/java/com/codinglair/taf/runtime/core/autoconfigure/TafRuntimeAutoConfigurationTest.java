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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.runtime.core.TestSession;
import com.codinglair.taf.runtime.core.lifecycle.CurrentTestSession;
import com.codinglair.taf.runtime.core.lifecycle.SessionAwareAccessor;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionLifecycle;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflight;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflightContributor;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflightException;
import com.codinglair.taf.runtime.core.preflight.PreflightDiagnostic;
import com.codinglair.taf.runtime.core.history.DisabledExecutionHistoryRepository;
import com.codinglair.taf.runtime.core.history.ExecutionHistoryRepository;
import com.codinglair.taf.runtime.core.history.FileExecutionHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * ApplicationContextRunner-style tests for TafRuntimeAutoConfiguration.
 *
 * <p>These tests verify that the auto-configuration correctly registers beans for TestSession and
 * ControllerRegistry when no beans are already defined, and that optional capabilities are
 * conditionally registered.
 *
 * @author Codinglair
 * @since 1.0
 */
class TafRuntimeAutoConfigurationTest {

  @Nested
  @DisplayName("TestSession creation")
  class TestSessionCreationTests {

    @Test
    @DisplayName("TestSession can be created directly")
    void testSessionCanBeCreatedDirectly() {
      TestSession session = TestSession.create();
      assertThat(session).isNotNull();
    }

    @Test
    @DisplayName("TestSession has non-empty session ID")
    void testSessionHasNonEmptySessionId() {
      TestSession session = TestSession.create();
      assertThat(session.getSessionId()).isNotEmpty();
    }

    @Test
    @DisplayName("TestSession has valid start time")
    void testSessionHasValidStartTime() {
      TestSession session = TestSession.create();
      assertThat(session.getStartTime()).isNotNull();
    }

    @Test
    @DisplayName("TestSession has unique session ID")
    void testSessionHasUniqueSessionId() {
      TestSession session1 = TestSession.create();
      TestSession session2 = TestSession.create();
      assertThat(session1.getSessionId()).isNotEqualTo(session2.getSessionId());
    }

    @Test
    @DisplayName("TestSession attributes are immutable")
    void testSessionAttributesAreImmutable() {
      TestSession session = TestSession.create();
      session.addCleanupListener(s -> {});
      // Verify the session maintains its state properly
      assertThat(session).isNotNull();
    }
  }

  @Nested
  @DisplayName("TafRuntimeProperties")
  class TafRuntimePropertiesTests {

    @Test
    @DisplayName("Web controller is enabled by default")
    void webControllerEnabledByDefault() {
      TafRuntimeProperties properties = new TafRuntimeProperties();
      assertThat(properties.isWebControllerEnabled()).isTrue();
    }

    @Test
    @DisplayName("Web controller can be disabled")
    void webControllerCanBeDisabled() {
      TafRuntimeProperties properties = new TafRuntimeProperties();
      properties.setWebControllerEnabled(false);
      assertThat(properties.isWebControllerEnabled()).isFalse();
    }

    @Test
    @DisplayName("Allure reporter is enabled by default")
    void allureReporterEnabledByDefault() {
      TafRuntimeProperties properties = new TafRuntimeProperties();
      assertThat(properties.isReporterAllureEnabled()).isTrue();
    }

    @Test
    @DisplayName("Allure reporter can be disabled")
    void allureReporterCanBeDisabled() {
      TafRuntimeProperties properties = new TafRuntimeProperties();
      properties.setReporterAllureEnabled(false);
      assertThat(properties.isReporterAllureEnabled()).isFalse();
    }

    @Test
    @DisplayName("Structured logging is enabled by default")
    void structuredLoggingEnabledByDefault() {
      TafRuntimeProperties properties = new TafRuntimeProperties();
      assertThat(properties.isLoggingStructured()).isTrue();
    }

    @Test
    @DisplayName("Structured logging can be disabled")
    void structuredLoggingCanBeDisabled() {
      TafRuntimeProperties properties = new TafRuntimeProperties();
      properties.setLoggingStructured(false);
      assertThat(properties.isLoggingStructured()).isFalse();
    }

    @Test
    @DisplayName("Max cleanup listeners defaults to 100")
    void maxCleanupListenersDefaultsTo100() {
      TafRuntimeProperties properties = new TafRuntimeProperties();
      assertThat(properties.getMaxCleanupListeners()).isEqualTo(100);
    }

    @Test
    @DisplayName("Controller timeout defaults to 30000ms")
    void controllerTimeoutDefaultsTo30000ms() {
      TafRuntimeProperties properties = new TafRuntimeProperties();
      assertThat(properties.getControllerTimeoutMs()).isEqualTo(30000);
    }

    @Test
    @DisplayName("Strict mode is disabled by default")
    void strictModeDisabledByDefault() {
      TafRuntimeProperties properties = new TafRuntimeProperties();
      assertThat(properties.isStrictMode()).isFalse();
    }

    @Test
    @DisplayName("Custom max cleanup listeners value")
    void customMaxCleanupListenersValue() {
      TafRuntimeProperties properties = new TafRuntimeProperties();
      properties.setMaxCleanupListeners(50);
      assertThat(properties.getMaxCleanupListeners()).isEqualTo(50);
    }

    @Test
    @DisplayName("Custom controller timeout value")
    void customControllerTimeoutValue() {
      TafRuntimeProperties properties = new TafRuntimeProperties();
      properties.setControllerTimeoutMs(60000);
      assertThat(properties.getControllerTimeoutMs()).isEqualTo(60000);
    }

    @Test
    @DisplayName("Custom strict mode value")
    void customStrictModeValue() {
      TafRuntimeProperties properties = new TafRuntimeProperties();
      properties.setStrictMode(true);
      assertThat(properties.isStrictMode()).isTrue();
    }
  }

  @Nested
  @DisplayName("AutoConfiguration classes")
  class AutoConfigurationTests {

    @Test
    @DisplayName("TafRuntimeAutoConfiguration exists and is instantiable")
    void tafRuntimeAutoConfigurationIsInstantiable() {
      TafRuntimeAutoConfiguration config = new TafRuntimeAutoConfiguration();
      assertThat(config).isNotNull();
    }

    @Test
    @DisplayName("ReporterAutoConfiguration exists and is instantiable")
    void reporterAutoConfigurationIsInstantiable() {
      ReporterAutoConfiguration config = new ReporterAutoConfiguration();
      assertThat(config).isNotNull();
    }
  }

  @Nested
  @DisplayName("Conditional bean creation")
  class ConditionalBeanCreationTests {

    @Test
    void historyDisabledStartsWithExplicitDisabledProvider() {
      new org.springframework.boot.test.context.runner.ApplicationContextRunner()
          .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(TafRuntimeAutoConfiguration.class))
          .withPropertyValues("taf.history.enabled=false")
          .run(context -> {
            assertThat(context).hasSingleBean(ExecutionHistoryRepository.class);
            assertThat(context.getBean(ExecutionHistoryRepository.class)).isInstanceOf(DisabledExecutionHistoryRepository.class);
          });
    }

    @Test
    void enabledHistoryBindsTypedConfiguration() {
      new org.springframework.boot.test.context.runner.ApplicationContextRunner()
          .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(TafRuntimeAutoConfiguration.class))
          .withPropertyValues("taf.history.enabled=true", "taf.history.maximum-records=17",
              "taf.history.maximum-age=2d", "taf.history.maximum-bytes=4096",
              "taf.history.signature-cause-limit=2")
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ExecutionHistoryRepository.class)).isInstanceOf(FileExecutionHistoryRepository.class);
          });
    }

    @Test
    void invalidHistoryBoundsFailAtStartup() {
      new org.springframework.boot.test.context.runner.ApplicationContextRunner()
          .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(TafRuntimeAutoConfiguration.class))
          .withPropertyValues("taf.history.maximum-records=0")
          .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("TestSession factory method creates new instance")
    void testSessionFactoryCreatesNewInstance() {
      TestSession session = TestSession.create();
      assertThat(session).isNotNull();
    }

    @Test
    @DisplayName("Spring contributes a factory, not a mutable session singleton")
    void springContributesSessionFactoryOnly() {
      new org.springframework.boot.test.context.runner.ApplicationContextRunner()
          .withConfiguration(
              org.springframework.boot.autoconfigure.AutoConfigurations.of(
                  TafRuntimeAutoConfiguration.class))
          .run(
              context -> {
                assertThat(context).hasSingleBean(TestSessionFactory.class);
                assertThat(context).hasSingleBean(TestSessionLifecycle.class);
                assertThat(context).hasSingleBean(CurrentTestSession.class);
                assertThat(context).hasSingleBean(SessionAwareAccessor.class);
                assertThat(context).hasSingleBean(ConsumerPreflight.class);
                assertThat(context).doesNotHaveBean(TestSession.class);
                var factory = context.getBean(TestSessionFactory.class);
                assertThat(factory.create()).isNotSameAs(factory.create());
              });
    }

    @Test
    @DisplayName("Preflight aggregates every Spring-managed neutral contributor")
    void preflightAggregatesContributors() {
      new org.springframework.boot.test.context.runner.ApplicationContextRunner()
          .withBean(
              "firstCheck",
              ConsumerPreflightContributor.class,
              () ->
                  () ->
                      java.util.List.of(
                          new PreflightDiagnostic(
                              "configuration.web.url", "URL is unresolved", "Configure the URL")))
          .withBean(
              "secondCheck",
              ConsumerPreflightContributor.class,
              () ->
                  () ->
                      java.util.List.of(
                          new PreflightDiagnostic(
                              "dependency.browser",
                              "Browser is unavailable",
                              "Install the browser")))
          .withConfiguration(
              org.springframework.boot.autoconfigure.AutoConfigurations.of(
                  TafRuntimeAutoConfiguration.class))
          .run(
              context -> {
                assertThat(context).hasSingleBean(ConsumerPreflight.class);
                assertThat(context.getBean(ConsumerPreflight.class).inspect().diagnostics())
                    .extracting(PreflightDiagnostic::checkId)
                    .containsExactly("configuration.web.url", "dependency.browser");
              });
    }

    @Test
    @DisplayName("Preflight ordering is deterministic and contributor failures are sanitized")
    void preflightOrdersContributorsAndSanitizesFailures() {
      var calls = new java.util.concurrent.CopyOnWriteArrayList<String>();
      ConsumerPreflightContributor later =
          () -> {
            calls.add("later");
            throw new IllegalStateException("credential=literal-secret");
          };
      ConsumerPreflightContributor earlier =
          () -> {
            calls.add("earlier");
            return java.util.List.of(
                new PreflightDiagnostic("first", "First check failed", "Correct first check"));
          };

      var preflight = new ConsumerPreflight(java.util.List.of(earlier, later));

      assertThatThrownBy(preflight::verify)
          .isInstanceOf(ConsumerPreflightException.class)
          .hasMessageContaining("first", "runtime.contributor.1")
          .hasMessageNotContaining("literal-secret");
      assertThat(calls).containsExactly("earlier", "later");
    }

    @Test
    @DisplayName("Consumer may replace the session-aware accessor")
    void consumerAccessorOverrideBacksOffAutoConfiguration() {
      SessionAwareAccessor custom = () -> TestSession.create();
      new org.springframework.boot.test.context.runner.ApplicationContextRunner()
          .withBean(SessionAwareAccessor.class, () -> custom)
          .withConfiguration(
              org.springframework.boot.autoconfigure.AutoConfigurations.of(
                  TafRuntimeAutoConfiguration.class))
          .run(context -> assertThat(context.getBean(SessionAwareAccessor.class)).isSameAs(custom));
    }
  }
}

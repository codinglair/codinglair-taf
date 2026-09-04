package com.codinglair.taf.runtime.environment.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.environment.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

class EnvironmentAutoConfigurationTest {
  private final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  TafRuntimeAutoConfiguration.class, EnvironmentAutoConfiguration.class));

  @Test
  void externalModeStartsWithoutTestcontainersBeans() {
    context.run(
        result -> {
          assertThat(result).hasSingleBean(EnvironmentRegistry.class);
          assertThat(result).doesNotHaveBean(ContainerLifecycleCoordinator.class);
          assertThat(result).hasSingleBean(SessionEnvironmentManager.class);
        });
  }

  @Test
  void scaffoldProfilesLoadWithUnresolvedValuesAndFailOnlyAtControlledPreflight() {
    scaffoldContext("application-local.yaml")
        .run(
            result -> {
              assertThat(result).hasNotFailed();
              var properties = result.getBean(ConsumerConfigurationProperties.class);
              assertThat(properties.getEnvironment()).isEqualTo("local");
              assertThat(properties.getCapabilities()).containsOnlyKeys("storefront", "payments");
              assertThatThrownBy(result.getBean(ConsumerPreflight.class)::verify)
                  .isInstanceOf(ConsumerPreflightException.class)
                  .hasMessageContaining("configuration.storefront.base-url")
                  .hasMessageContaining("configuration.storefront.username")
                  .hasMessageNotContaining("configuration.payments");
            });
    scaffoldContext("application-ci.yaml")
        .run(
            result -> {
              assertThat(result).hasNotFailed();
              assertThat(result.getBean(ConsumerConfigurationProperties.class).getEnvironment())
                  .isEqualTo("ci");
              assertThat(result).doesNotHaveBean(ContainerLifecycleCoordinator.class);
            });
  }

  @Test
  void consumerPreflightContractsBackOffAndContributorRunsOnlyForSelectedMatchingType() {
    SelectedContributor.CALLS.set(0);
    context
        .withUserConfiguration(ConsumerOverrides.class, SelectedContributor.class)
        .withPropertyValues(
            "taf.consumer.capabilities.selected.type=web-playwright",
            "taf.consumer.capabilities.selected.enabled=true",
            "taf.consumer.capabilities.unselected.type=web-playwright",
            "taf.consumer.capabilities.unselected.enabled=false")
        .run(
            result -> {
              assertThat(result)
                  .hasSingleBean(com.codinglair.taf.runtime.core.preflight.ConsumerPreflight.class);
              assertThat(result).hasSingleBean(ConsumerPreflight.class);
              assertThat(result.getBean(SecretReferenceAvailability.class))
                  .isSameAs(ConsumerOverrides.SECRETS);
              var core =
                  result.getBean(com.codinglair.taf.runtime.core.preflight.ConsumerPreflight.class);
              var legacy = result.getBean(ConsumerPreflight.class);
              var coreChecks = core.inspect().diagnostics();
              assertThat(SelectedContributor.CALLS).hasValue(1);
              SelectedContributor.CALLS.set(0);
              var legacyChecks = legacy.inspect().checks();
              assertThat(SelectedContributor.CALLS).hasValue(1);
              assertThat(coreChecks)
                  .extracting(
                      com.codinglair.taf.runtime.core.preflight.PreflightDiagnostic::checkId)
                  .containsExactlyElementsOf(
                      legacyChecks.stream().map(PreflightCheckResult::checkId).toList());
            });
  }

  @Test
  void legacyFacadeVerifyDelegatesOnceToCoreAggregator() {
    SelectedContributor.CALLS.set(0);
    context
        .withUserConfiguration(SelectedContributor.class)
        .withPropertyValues("taf.consumer.capabilities.selected.type=web-playwright")
        .run(
            result -> {
              assertThatThrownBy(result.getBean(ConsumerPreflight.class)::verify)
                  .isInstanceOf(ConsumerPreflightException.class);
              assertThat(SelectedContributor.CALLS).hasValue(1);
            });
  }

  @Test
  void containerCompositionRequiresBothModeAndEnablement() {
    context
        .withPropertyValues("taf.environment.testcontainers-enabled=true")
        .run(result -> assertThat(result).doesNotHaveBean(ContainerLifecycleCoordinator.class));
    context
        .withPropertyValues(
            "taf.environment.mode=container", "taf.environment.testcontainers-enabled=true")
        .run(
            result -> {
              assertThat(result).hasSingleBean(ContainerLifecycleCoordinator.class);
              assertThat(result).hasSingleBean(TestcontainersEnvironmentProviderFactory.class);
            });
  }

  @Test
  void consumerOverridesDefaults() {
    context
        .withUserConfiguration(Overrides.class)
        .run(
            result -> {
              assertThat(result).hasSingleBean(EnvironmentRegistry.class);
              assertThat(result.getBean(EnvironmentRegistry.class)).isSameAs(Overrides.REGISTRY);
              assertThat(result).hasSingleBean(SessionEnvironmentManager.class);
            });
  }

  @Test
  void bindsAndValidatesProperties() {
    context
        .withPropertyValues(
            "taf.environment.mode=container",
            "taf.environment.lifecycle=shared",
            "taf.environment.startup=eager",
            "taf.environment.readiness-timeout=5s",
            "taf.environment.log-capture=false",
            "taf.environment.cleanup=on-success")
        .run(
            result -> {
              var properties = result.getBean(TafEnvironmentProperties.class);
              assertThat(properties.getMode()).isEqualTo(EnvironmentMode.CONTAINER);
              assertThat(properties.getLifecycle()).isEqualTo(ContainerLifecycle.SHARED);
              assertThat(properties.getReadinessTimeout()).isEqualTo(Duration.ofSeconds(5));
              assertThat(properties.isLogCapture()).isFalse();
            });
    context
        .withPropertyValues("taf.environment.readiness-timeout=0s")
        .run(result -> assertThat(result).hasFailed());
  }

  @Test
  void sharedResourceStartsBeforeAndStopsAfterDependentBean() {
    SharedOrdering.EVENTS.clear();
    context
        .withUserConfiguration(SharedOrdering.class)
        .withPropertyValues(
            "taf.environment.mode=container",
            "taf.environment.testcontainers-enabled=true",
            "taf.environment.startup=eager")
        .run(
            result -> {
              assertThat(result).hasSingleBean(EnvironmentResource.class);
              assertThat(SharedOrdering.EVENTS).containsExactly("start", "client-create");
            });
    assertThat(SharedOrdering.EVENTS)
        .containsExactly("start", "client-create", "client-destroy", "stop");
  }

  @Configuration(proxyBeanMethods = false)
  static class Overrides {
    static final EnvironmentRegistry REGISTRY = new DefaultEnvironmentRegistry();

    @Bean
    EnvironmentRegistry customRegistry() {
      return REGISTRY;
    }

    @Bean
    SessionEnvironmentManager customSessionManager(EnvironmentRegistry registry) {
      return new SessionEnvironmentManager(registry);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class ConsumerOverrides {
    static final SecretReferenceAvailability SECRETS = reference -> true;

    @Bean
    SecretReferenceAvailability secretReferenceAvailability() {
      return SECRETS;
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class SelectedContributor {
    static final AtomicInteger CALLS = new AtomicInteger();

    @Bean
    ConsumerPreflightContributor selectedContributorBean() {
      return new ConsumerPreflightContributor() {
        @Override
        public String capabilityType() {
          return "web-playwright";
        }

        @Override
        public List<PreflightCheckResult> check(
            String name,
            ConsumerConfigurationProperties.CapabilityInstance instance,
            ConsumerConfigurationProperties configuration) {
          CALLS.incrementAndGet();
          return List.of(
              new PreflightCheckResult(
                  "legacy." + name,
                  PreflightCheckType.READINESS,
                  Optional.empty(),
                  EnvironmentStatus.UNAVAILABLE,
                  "Dependency is unavailable",
                  "Start the controlled dependency",
                  Map.of(),
                  Instant.now()));
        }
      };
    }
  }

  private ApplicationContextRunner scaffoldContext(String profileResource) {
    return context.withInitializer(
        applicationContext -> {
          try {
            var loader = new YamlPropertySourceLoader();
            var sources = applicationContext.getEnvironment().getPropertySources();
            loader
                .load("consumer-base", new ClassPathResource("consumer-config/application.yaml"))
                .forEach(sources::addLast);
            loader
                .load(
                    "consumer-profile", new ClassPathResource("consumer-config/" + profileResource))
                .forEach(sources::addFirst);
          } catch (java.io.IOException failure) {
            throw new IllegalStateException("Could not load scaffold profile fixture", failure);
          }
        });
  }

  @Configuration(proxyBeanMethods = false)
  static class SharedOrdering {
    static final List<String> EVENTS = new CopyOnWriteArrayList<>();
    static final EnvironmentType TYPE = new EnvironmentType("ordered");

    @Bean
    EnvironmentProvider provider() {
      return new OrderedProvider();
    }

    @Bean
    EnvironmentRegistry registry(EnvironmentProvider provider) {
      var registry = new DefaultEnvironmentRegistry();
      registry.register(TYPE, EnvironmentMode.CONTAINER, provider);
      return registry;
    }

    @Bean
    SharedEnvironmentRequest request() {
      return new SharedEnvironmentRequest(
          new EnvironmentRequest(
              "ordered",
              TYPE,
              EnvironmentMode.CONTAINER,
              Set.of(),
              Map.of(ContainerLifecycle.PROPERTY, "shared"),
              Duration.ofSeconds(2)));
    }

    @Bean
    DependentClient client(EnvironmentResource resource) {
      EVENTS.add("client-create");
      return new DependentClient();
    }

    static final class DependentClient implements DisposableBean {
      @Override
      public void destroy() {
        EVENTS.add("client-destroy");
      }
    }

    static final class OrderedProvider extends AbstractEnvironmentProvider {
      public String id() {
        return "ordered";
      }

      public Set<EnvironmentMode> supportedModes() {
        return Set.of(EnvironmentMode.CONTAINER);
      }

      public PreflightResult preflight(EnvironmentRequest request) {
        return PreflightResult.from(List.of());
      }

      protected EnvironmentResource create(EnvironmentRequest request) {
        EVENTS.add("start");
        return new EnvironmentResource() {
          private final AtomicBoolean closed = new AtomicBoolean();

          public String id() {
            return "ordered-resource";
          }

          public EnvironmentType type() {
            return TYPE;
          }

          public EnvironmentMode mode() {
            return EnvironmentMode.CONTAINER;
          }

          public Map<String, String> properties() {
            return Map.of("ordered.ready", "true");
          }

          public EnvironmentDiagnostic diagnose() {
            return new EnvironmentDiagnostic(
                EnvironmentStatus.READY, "ready", "", Map.of(), Instant.now());
          }

          public void cleanup() {
            if (closed.compareAndSet(false, true)) EVENTS.add("stop");
          }
        };
      }
    }
  }
}

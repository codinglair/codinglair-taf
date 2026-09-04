package com.codinglair.taf.runtime.definition.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.definition.TestDefinition;
import com.codinglair.taf.runtime.definition.TestDefinitionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;

@DisplayName("MongoDB test-definition auto-configuration")
class MongoTestDefinitionAutoConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(MongoTestDefinitionAutoConfiguration.class))
          .withBean(ObjectMapper.class, ObjectMapper::new);

  @Test
  @DisplayName("declares deterministic lowest-precedence auto-configuration ordering")
  void declaresExplicitOrdering() {
    AutoConfigureOrder order =
        AnnotatedElementUtils.findMergedAnnotation(
            MongoTestDefinitionAutoConfiguration.class, AutoConfigureOrder.class);

    assertThat(order).isNotNull();
    assertThat(order.value()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
  }

  @Test
  @DisplayName("publishes generated configuration metadata for MongoDB properties")
  void publishesConfigurationMetadata() throws Exception {
    Path metadata = Path.of("target", "classes", "META-INF", "spring-configuration-metadata.json");

    assertThat(metadata).exists().isRegularFile();
    assertThat(Files.readString(metadata))
        .contains("taf.context.mongodb.project", "taf.context.mongodb.schema-version");
  }

  @Nested
  @DisplayName("Provider selection")
  class ProviderSelection {
    @Test
    @DisplayName("does not load MongoDB infrastructure in file mode")
    void fileModeRemainsMongoFree() {
      runner
          .withPropertyValues("taf.context.repository=file")
          .run(
              context -> {
                assertThat(context).doesNotHaveBean(MongoClient.class);
                assertThat(context).doesNotHaveBean(MongoTestDefinitionRepository.class);
              });
    }

    @Test
    @DisplayName("backs off when the consumer supplies the repository and client")
    void consumerBeansTakePrecedence() {
      runner
          .withUserConfiguration(ConsumerConfiguration.class)
          .withPropertyValues("taf.context.repository=mongodb")
          .run(
              context -> {
                assertThat(context).hasSingleBean(TestDefinitionRepository.class);
                assertThat(context).doesNotHaveBean(MongoTestDefinitionRepository.class);
                assertThat(context).doesNotHaveBean(MongoDefinitionSynchronizer.class);
              });
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class ConsumerConfiguration {
    @Bean
    MongoClient mongoClient() {
      return (MongoClient)
          Proxy.newProxyInstance(
              MongoClient.class.getClassLoader(),
              new Class<?>[] {MongoClient.class},
              (_, method, _) -> {
                if (method.getName().equals("close")) return null;
                throw new UnsupportedOperationException(method.getName());
              });
    }

    @Bean
    TestDefinitionRepository repository() {
      return new TestDefinitionRepository() {
        @Override
        public <I, E> TestDefinition<I, E> require(
            String caseId, Class<I> inputType, Class<E> expectedOutputType) {
          throw new UnsupportedOperationException();
        }
      };
    }
  }
}

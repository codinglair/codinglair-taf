package com.codinglair.taf.runtime.definition.mongodb;

import com.codinglair.taf.migration.MongoContextMigration;
import com.codinglair.taf.runtime.definition.TestDefinitionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/** Optional composition for the reserved TAF-context MongoDB provider. */
@AutoConfiguration
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnClass(MongoClient.class)
@ConditionalOnProperty(prefix = "taf.context", name = "repository", havingValue = "mongodb")
@EnableConfigurationProperties(MongoTestDefinitionProperties.class)
public class MongoTestDefinitionAutoConfiguration {
  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean
  MongoClient tafContextMongoClient(MongoTestDefinitionProperties properties) {
    return MongoClients.create(properties.getUri());
  }

  @Bean
  @ConditionalOnMissingBean(TestDefinitionRepository.class)
  MongoTestDefinitionRepository mongoTestDefinitionRepository(
      MongoClient client,
      MongoTestDefinitionProperties properties,
      ObjectMapper mapper,
      ObjectProvider<MongoContextMigration> migration) {
    MongoContextMigration readiness = migration.getIfAvailable();
    if (properties.isRequireMigration() && readiness == null) {
      throw new IllegalStateException("Mongo repository requires the approved migration preflight");
    }
    if (readiness != null) readiness.ensureReady();
    return new MongoTestDefinitionRepository(
        client.getDatabase(properties.getDatabase()), properties, mapper);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(MongoTestDefinitionRepository.class)
  MongoDefinitionSynchronizer mongoDefinitionSynchronizer(
      MongoTestDefinitionRepository repository, ObjectMapper mapper) {
    return new MongoDefinitionSynchronizer(repository, mapper);
  }
}

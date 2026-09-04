package com.codinglair.taf.migration;

import com.codinglair.taf.database.ConnectionMode;
import com.codinglair.taf.database.JdbcConnectionFactory;
import com.codinglair.taf.database.SutConnectionDescriptor;
import com.codinglair.taf.database.SutConnectionRegistry;
import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.migration.DataMigrationManager;
import com.codinglair.taf.runtime.core.migration.MigrationPolicy;
import com.codinglair.taf.runtime.core.migration.MigrationRequest;
import com.codinglair.taf.runtime.core.migration.MigrationTarget;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflightContributor;
import com.codinglair.taf.runtime.core.preflight.PreflightDiagnostic;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

@AutoConfiguration
@AutoConfigureAfter({
  TafRuntimeAutoConfiguration.class,
  com.codinglair.taf.database.DatabaseAutoConfiguration.class
})
@ConditionalOnClass(Flyway.class)
@ConditionalOnProperty(prefix = "taf.migration", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({MigrationProperties.class, MongoContainerMigrationProperties.class})
public class DataMigrationAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  MigrationAuthorization migrationAuthorization() {
    return request -> request.target() == MigrationTarget.TESTCONTAINERS_SUT;
  }

  @Bean
  @ConditionalOnMissingBean
  DataMigrationManager dataMigrationManager(
      SutConnectionRegistry registry,
      JdbcConnectionFactory connections,
      MigrationAuthorization authorization,
      MongoContainerMigrationProperties mongoProperties,
      ObjectProvider<ObjectMapper> objectMapper,
      ObjectProvider<MongoContextDestroyer> contextDestroyer) {
    DataMigrationManager relational =
        new FlywayDataMigrationManager(registry, connections, authorization);
    if (!mongoProperties.isEnabled()) return relational;
    MongoContextDestroyer destroyer = contextDestroyer.getIfAvailable();
    if (destroyer == null) {
      throw new IllegalStateException(
          "Mongo context migration requires an environment-owned MongoContextDestroyer");
    }
    DataMigrationManager mongo =
        new ContainerizedMongoDataMigrationManager(
            mongoProperties,
            new TestcontainersMongoMigrationJobExecutor(),
            new MongoMigrationCoordinator(),
            objectMapper.getIfAvailable(ObjectMapper::new),
            destroyer);
    return new RoutingDataMigrationManager(relational, mongo);
  }

  @Bean
  @ConditionalOnProperty(prefix = "taf.migration.mongodb", name = "enabled", havingValue = "true")
  MongoContextMigration mongoContextMigration(
      DataMigrationManager manager, MongoContainerMigrationProperties properties) {
    return new MongoContextMigration(manager, properties);
  }

  @Bean
  @Order(190)
  @ConditionalOnProperty(prefix = "taf.migration.mongodb", name = "enabled", havingValue = "true")
  ConsumerPreflightContributor mongoMigrationPreflightContributor(
      MongoContainerMigrationProperties properties, MongoContextMigration migration) {
    return () -> {
      if (!properties.isEnabled() || properties.getPolicy() == MigrationPolicy.DISABLED)
        return List.of();
      try {
        migration.ensureReady();
        return List.of();
      } catch (RuntimeException _) {
        return List.of(
            new PreflightDiagnostic(
                "migration.mongodb." + properties.getTargetIdentity(),
                "Mongo context migration failed",
                "correct the approved context migration configuration and recreate the context"));
      }
    };
  }

  @Bean
  @Order(200)
  ConsumerPreflightContributor migrationPreflightContributor(
      MigrationProperties properties,
      SutConnectionRegistry registry,
      DataMigrationManager manager,
      Environment environment) {
    return () -> {
      List<PreflightDiagnostic> diagnostics = new ArrayList<>();
      properties
          .getConnections()
          .forEach(
              (name, configured) -> {
                if (configured.getPolicy() == MigrationPolicy.DISABLED) return;
                try {
                  SutConnectionDescriptor descriptor = registry.require(name);
                  MigrationRequest request = request(descriptor, configured, environment);
                  var result = manager.execute(request);
                  if (!result.validation().valid())
                    diagnostics.add(
                        new PreflightDiagnostic(
                            "migration." + name,
                            result.validation().diagnostics().getFirst(),
                            "correct migration configuration, authorization, or Git-managed scripts"));
                } catch (RuntimeException _) {
                  diagnostics.add(
                      new PreflightDiagnostic(
                          "migration." + name,
                          "Migration preflight could not inspect the logical target",
                          "correct named connection and migration configuration"));
                }
              });
      return List.copyOf(diagnostics);
    };
  }

  private static MigrationRequest request(
      SutConnectionDescriptor descriptor,
      MigrationProperties.Connection configured,
      Environment environment) {
    String environmentName = environment.getProperty("taf.database.environment", "local");
    MigrationTarget target =
        environmentName.equalsIgnoreCase("production")
            ? MigrationTarget.PRODUCTION_SUT
            : descriptor.mode() == ConnectionMode.TESTCONTAINERS
                ? MigrationTarget.TESTCONTAINERS_SUT
                : MigrationTarget.EXTERNAL_SHARED_SUT;
    return new MigrationRequest(
        descriptor.name(),
        descriptor.technology(),
        target,
        configured.getPolicy(),
        configured.getLocations());
  }
}

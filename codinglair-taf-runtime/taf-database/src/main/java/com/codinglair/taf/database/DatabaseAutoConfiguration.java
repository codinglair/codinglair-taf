package com.codinglair.taf.database;

import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionConfigurer;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflightContributor;
import com.codinglair.taf.runtime.core.preflight.PreflightDiagnostic;
import com.codinglair.taf.runtime.secret.SecretManager;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
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
@AutoConfigureAfter(TafRuntimeAutoConfiguration.class)
@ConditionalOnClass(DriverManager.class)
@ConditionalOnProperty(prefix = "taf.database", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(DatabaseProperties.class)
public class DatabaseAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  SutConnectionRegistry sutConnectionRegistry(
      DatabaseProperties properties, Environment environment) {
    return new SutConnectionRegistry(
        properties.getConnections().entrySet().stream()
            .map(entry -> entry.getValue().descriptor(entry.getKey()))
            .toList(),
        environment.getProperty("taf.context.jdbc-url"));
  }

  @Bean
  @ConditionalOnMissingBean
  JdbcConnectionFactory jdbcConnectionFactory(
      DatabaseProperties properties, ObjectProvider<SecretManager> secrets) {
    return new DefaultJdbcConnectionFactory(secrets.getIfAvailable(), properties.getEnvironment());
  }

  @Bean
  TestSessionConfigurer databaseSessionConfigurer(
      SutConnectionRegistry registry, JdbcConnectionFactory factory) {
    return session ->
        registry
            .connections()
            .forEach(
                descriptor ->
                    session
                        .getControllerRegistry()
                        .register(
                            DatabaseController.class,
                            descriptor.name(),
                            new DefaultDatabaseController(descriptor, factory)));
  }

  @Bean
  @Order(100)
  ConsumerPreflightContributor databasePreflightContributor(
      SutConnectionRegistry registry, ObjectProvider<SecretManager> secrets) {
    return () -> {
      List<PreflightDiagnostic> diagnostics = new ArrayList<>();
      for (SutConnectionDescriptor descriptor : registry.connections())
        if (!descriptor.passwordReference().isBlank()) {
          SecretManager manager = secrets.getIfAvailable();
          if (manager == null)
            diagnostics.add(
                diagnostic(
                    descriptor.name(),
                    "SecretManager is unavailable",
                    "configure a secret provider"));
          else
            try {
              manager.verifyReady(descriptor.passwordReference());
            } catch (RuntimeException ignored) {
              diagnostics.add(
                  diagnostic(
                      descriptor.name(),
                      "Credential reference is not ready",
                      "correct the secret reference or provider readiness"));
            }
        }
      return List.copyOf(diagnostics);
    };
  }

  private static PreflightDiagnostic diagnostic(String name, String summary, String action) {
    return new PreflightDiagnostic("database." + name, summary, action);
  }
}

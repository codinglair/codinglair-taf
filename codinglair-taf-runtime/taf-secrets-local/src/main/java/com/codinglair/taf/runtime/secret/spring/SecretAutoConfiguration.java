package com.codinglair.taf.runtime.secret.spring;

import com.codinglair.taf.runtime.core.preflight.ConsumerPreflightContributor;
import com.codinglair.taf.runtime.secret.DefaultSecretManager;
import com.codinglair.taf.runtime.secret.EnvironmentSecretProvider;
import com.codinglair.taf.runtime.secret.EnvironmentValueSource;
import com.codinglair.taf.runtime.secret.JasyptSecretProvider;
import com.codinglair.taf.runtime.secret.SecretAuditSink;
import com.codinglair.taf.runtime.secret.SecretManager;
import com.codinglair.taf.runtime.secret.SecretProvider;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(SecretProperties.class)
public class SecretAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  EnvironmentValueSource environmentValueSource() {
    return System::getenv;
  }

  @Bean
  EnvironmentSecretProvider environmentSecretProvider(EnvironmentValueSource source) {
    return new EnvironmentSecretProvider(source);
  }

  @Bean
  JasyptSecretProvider jasyptSecretProvider(
      EnvironmentValueSource source, SecretProperties properties) {
    return new JasyptSecretProvider(source, properties.getJasyptMasterKeyEnvironmentVariable());
  }

  @Bean
  @ConditionalOnMissingBean
  SecretAuditSink secretAuditSink() {
    return SecretAuditSink.NO_OP;
  }

  @Bean
  @ConditionalOnMissingBean
  SecretManager secretManager(List<SecretProvider> providers, SecretAuditSink audit) {
    return new DefaultSecretManager(providers, audit);
  }

  @Bean
  ConsumerPreflightContributor secretPreflightContributor(
      SecretProperties properties, List<SecretProvider> providers) {
    return new SecretPreflightContributor(properties, providers);
  }
}

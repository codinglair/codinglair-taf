package com.codinglair.taf.runtime.secret.spring;

import com.codinglair.taf.runtime.core.preflight.ConsumerPreflightContributor;
import com.codinglair.taf.runtime.secret.DefaultSecretManager;
import com.codinglair.taf.runtime.secret.EnvironmentSecretProvider;
import com.codinglair.taf.runtime.secret.EnvironmentValueSource;
import com.codinglair.taf.runtime.secret.JasyptSecretProvider;
import com.codinglair.taf.runtime.secret.SecretAuditSink;
import com.codinglair.taf.runtime.secret.SecretManager;
import com.codinglair.taf.runtime.secret.SecretProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

@AutoConfiguration
@EnableConfigurationProperties(SecretProperties.class)
public class SecretAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  EnvironmentValueSource environmentValueSource() {
    return System::getenv;
  }

  @Bean
  @Conditional(EnvironmentProviderEnabled.class)
  EnvironmentSecretProvider environmentSecretProvider(EnvironmentValueSource source) {
    return new EnvironmentSecretProvider(source);
  }

  @Bean
  @Conditional(JasyptProviderEnabled.class)
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
  SecretManager secretManager(
      Map<String, SecretProvider> providers,
      SecretProperties properties,
      SecretAuditSink audit,
      Environment environment) {
    return new DefaultSecretManager(selectProviders(providers, properties, environment), audit);
  }

  @Bean
  ConsumerPreflightContributor secretPreflightContributor(
      SecretProperties properties, Map<String, SecretProvider> providers, Environment environment) {
    return new SecretPreflightContributor(
        properties, selectProviders(providers, properties, environment));
  }

  private static List<SecretProvider> selectProviders(
      Map<String, SecretProvider> available, SecretProperties properties, Environment environment) {
    String selected = normalized(properties.getProvider());
    Map<String, String> routing = properties.getRouting();
    boolean localProfile = environment.matchesProfiles("taf-local");
    if (selected == null && routing.isEmpty() && localProfile) selected = "env";
    if (selected == null && routing.isEmpty()) {
      throw selectionFailure(
          "taf.secrets.provider",
          "No secret provider is selected for the active profile",
          "Set taf.secrets.provider, configure taf.secrets.routing, or activate the documented taf-local profile");
    }
    if (selected != null && !routing.isEmpty()) {
      throw selectionFailure(
          "taf.secrets.provider/taf.secrets.routing",
          "Secret provider selection is ambiguous for the active profile",
          "Configure either one provider or explicit routing, not both");
    }
    if (selected != null) return List.of(findProvider(selected, available));

    Map<String, SecretProvider> routed = new LinkedHashMap<>();
    routing.forEach(
        (providerId, beanName) -> {
          String id = normalized(providerId);
          String bean = normalized(beanName);
          if (id == null || bean == null) {
            throw selectionFailure(
                "taf.secrets.routing",
                "A secret provider route is blank for the active profile",
                "Map each reference provider id to one SecretProvider bean name");
          }
          SecretProvider provider = available.get(bean);
          if (provider == null || !id.equals(provider.id())) {
            throw selectionFailure(
                "taf.secrets.routing",
                "A secret provider route does not identify a matching provider bean",
                "Map the provider id to an available SecretProvider bean with the same id");
          }
          if (routed.putIfAbsent(id, provider) != null) {
            throw selectionFailure(
                "taf.secrets.routing",
                "A secret provider route is duplicated",
                "Configure exactly one bean route for each provider id");
          }
        });
    return List.copyOf(routed.values());
  }

  private static SecretProvider findProvider(
      String selector, Map<String, SecretProvider> available) {
    SecretProvider named = available.get(selector);
    if (named != null) return named;
    List<SecretProvider> matches =
        available.values().stream().filter(provider -> selector.equals(provider.id())).toList();
    if (matches.size() == 1) return matches.getFirst();
    String message =
        matches.isEmpty()
            ? "The selected secret provider is unavailable for the active profile"
            : "The selected secret provider matches multiple beans for the active profile";
    throw selectionFailure(
        "taf.secrets.provider",
        message,
        "Select one available SecretProvider bean name or configure explicit routing");
  }

  private static IllegalStateException selectionFailure(
      String field, String message, String action) {
    return new IllegalStateException(
        "capability=secrets; field/profile="
            + field
            + "; "
            + message
            + "; corrective-action="
            + action);
  }

  private static String normalized(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }

  private abstract static class LocalProviderCondition implements Condition {
    private final String providerId;

    private LocalProviderCondition(String providerId) {
      this.providerId = providerId;
    }

    @Override
    public final boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
      Environment environment = context.getEnvironment();
      return providerId.equals(environment.getProperty("taf.secrets.provider"))
          || environment.containsProperty("taf.secrets.routing." + providerId)
          || ("env".equals(providerId)
              && environment.matchesProfiles("taf-local")
              && environment.getProperty("taf.secrets.provider") == null
              && !Binder.get(environment)
                  .bind("taf.secrets.routing", Bindable.mapOf(String.class, String.class))
                  .isBound());
    }
  }

  static final class EnvironmentProviderEnabled extends LocalProviderCondition {
    EnvironmentProviderEnabled() {
      super("env");
    }
  }

  static final class JasyptProviderEnabled extends LocalProviderCondition {
    JasyptProviderEnabled() {
      super("jasypt");
    }
  }
}

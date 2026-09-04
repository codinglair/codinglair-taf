package __BASE_PACKAGE__.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("consumer.environment")
public record ConsumerEnvironmentProperties(String name, String userSecretReference) {}

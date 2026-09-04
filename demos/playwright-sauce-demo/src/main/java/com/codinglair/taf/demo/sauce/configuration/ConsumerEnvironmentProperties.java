package com.codinglair.taf.demo.sauce.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("consumer.environment")
public record ConsumerEnvironmentProperties(String name, String userSecretReference) {}

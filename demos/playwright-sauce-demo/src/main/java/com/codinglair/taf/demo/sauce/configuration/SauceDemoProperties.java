package com.codinglair.taf.demo.sauce.configuration;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("taf.demo")
public record SauceDemoProperties(
    String baseUrl,
    Path reportOutput,
    String testDefinitionProvider,
    String testDataLocation,
    Duration actionTimeout,
    String passwordReference) {}

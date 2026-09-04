package com.codinglair.taf.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Simple configuration loader for environment-specific settings. Dependency-light: does not depend
 * on Spring for core contracts.
 */
public class AppConfigLoader {

  /**
   * Loads configuration properties for the given environment.
   *
   * @param env The environment name (e.g., "dev", "prod")
   * @return A map of configuration properties
   */
  public static Map<String, String> load(String env) {
    Map<String, String> config = new HashMap<>();

    // Default values for all environments
    config.put("taf.default.timeout", "30000");
    config.put("taf.default.screenshot.path", "./screenshots");
    config.put("taf.report.output.path", "./reports");

    // Environment-specific overrides
    switch (env.toLowerCase()) {
      case "prod":
        config.put("taf.default.timeout", "60000");
        config.put("taf.logging.level", "INFO");
        break;
      case "test":
        config.put("taf.logging.level", "DEBUG");
        break;
      case "dev":
        config.put("taf.logging.level", "DEBUG");
        config.put("taf.default.timeout", "120000");
        break;
      default:
        config.put("taf.logging.level", "INFO");
    }

    return config;
  }

  /**
   * Loads a specific configuration property.
   *
   * @param env The environment name
   * @param key The configuration key
   * @return The configuration value, or null if not found
   */
  public static String load(String env, String key) {
    Map<String, String> config = load(env);
    return config.get(key);
  }

  /**
   * Check if a configuration property exists.
   *
   * @param env The environment name
   * @param key The configuration key
   * @return true if the property exists
   */
  public static boolean hasProperty(String env, String key) {
    Map<String, String> config = load(env);
    return config.containsKey(key);
  }

  /**
   * Load configuration from a submodule.
   *
   * @param configPath The path to the configuration file/resource
   * @return The configuration path
   */
  public static String loadSubmoduleConfig(String configPath) {
    return configPath;
  }

  /**
   * Load configuration from a submodule as an Optional.
   *
   * @param configPath The path to the configuration file/resource
   * @return An Optional containing the configuration path
   */
  public static Optional<String> loadSubmoduleConfigOptional(String configPath) {
    return Optional.of(loadSubmoduleConfig(configPath));
  }

  /**
   * Load configuration from a submodule with a fallback.
   *
   * @param configPath The path to the configuration file/resource
   * @param fallback The fallback configuration path
   * @return The configuration path or fallback
   */
  public static String loadSubmoduleConfigOr(String configPath, String fallback) {
    return Optional.ofNullable(loadSubmoduleConfig(configPath)).orElse(fallback);
  }

  /**
   * Get a configuration property from the loaded submodule config.
   *
   * @param configPath The path to the configuration file/resource
   * @param key The configuration property key
   * @return The configuration property value, or null if not found
   */
  public static String getProperty(String configPath, String key) {
    // Default implementation - returns null
    // In a real implementation, this would load and parse the config
    return null;
  }

  /**
   * Get a configuration property with a fallback value.
   *
   * @param configPath The path to the configuration file/resource
   * @param key The configuration property key
   * @param defaultValue The default value to return if not found
   * @return The configuration property value or default
   */
  public static String getProperty(String configPath, String key, String defaultValue) {
    String value = getProperty(configPath, key);
    return value != null ? value : defaultValue;
  }
}

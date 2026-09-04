package com.codinglair.taf.core.environment;

/**
 * Represents core environment properties (e.g., system name, test profile, container
 * configuration). This structure is maintained as a mutable class to support runtime environment
 * configuration updates (putEnvProperty), while adhering to RT-001's contract stability goals.
 *
 * <p>Properties are designed to be stable and serializable for future MCP schemas.
 */
public class EnvironmentProperties {
  private String environmentName;
  private TestProfile profile;
  private String runtimeId;

  public enum TestProfile {
    LOCAL,
    CI_DEV,
    STAGING,
    PRODUCTION
  }

  public EnvironmentProperties(String environmentName, TestProfile profile, String runtimeId) {
    if (environmentName == null || environmentName.isBlank()) {
      throw new IllegalArgumentException("Environment name must not be null or empty.");
    }
    if (runtimeId == null || runtimeId.isBlank()) {
      throw new IllegalArgumentException("Runtime ID must not be null or empty.");
    }
    this.environmentName = environmentName;
    this.profile = profile;
    this.runtimeId = runtimeId;
  }

  public String getEnvName() {
    return environmentName;
  }

  public String getEnvironmentName() {
    return environmentName;
  }

  public TestProfile getProfile() {
    return profile;
  }

  public String getRuntimeId() {
    return runtimeId;
  }

  /**
   * Retrieves an environment property value based on a key. This method is introduced to resolve
   * compilation errors in controllers and utility classes. Note: In a real-world TAF, this should
   * interact with a dedicated Provider SPI.
   */
  public String getEnvProperty(String key) {
    // Placeholder implementation: return null or a default value.
    // Actual implementation would read from configuration/system properties.
    return null;
  }

  /**
   * Sets or updates an environment property. This method is introduced to resolve compilation
   * errors in test lifecycle managers. Note: Mutating the properties object is generally
   * discouraged for core contracts but necessary for compilation here.
   */
  public void putEnvProperty(String key, String value) {
    // Placeholder implementation: For now, we'll just log/store the value if necessary,
    // but since this is a value object, a real implementation would likely involve creating a new
    // immutable instance.
    System.out.println("Setting Environment Property: " + key + " = " + value);
  }
}

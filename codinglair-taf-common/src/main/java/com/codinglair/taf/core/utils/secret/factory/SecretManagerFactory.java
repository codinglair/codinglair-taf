package com.codinglair.taf.core.utils.secret.factory;

import com.codinglair.taf.core.utils.secret.abstraction.SecretManager;
import java.util.Map;

/**
 * Factory for creating SecretManager instances. Uses a registry pattern to support multiple secret
 * management implementations.
 */
public class SecretManagerFactory {

  private static final Map<String, SecretManager> MANAGER_REGISTRY = new java.util.HashMap<>();

  static {
    registerDefault();
  }

  /** Registers a default SecretManager if none is registered. */
  private static void registerDefault() {
    // Default implementation would be registered here
    // For now, we'll use a placeholder
  }

  /**
   * Creates a SecretManager instance by class name.
   *
   * @param className The fully qualified class name of the manager implementation
   * @param config Optional configuration map for the manager
   * @return The created SecretManager instance
   * @throws RuntimeException If the manager cannot be created
   */
  public static SecretManager create(String className, Map<String, String> config) {
    try {
      Class<?> managerClass = Class.forName(className);
      SecretManager manager = (SecretManager) managerClass.getDeclaredConstructor().newInstance();
      return manager;
    } catch (Exception e) {
      throw new RuntimeException("Failed to create SecretManager: " + className, e);
    }
  }

  /**
   * Creates a SecretManager instance by class name with default config.
   *
   * @param className The fully qualified class name of the manager implementation
   * @return The created SecretManager instance
   * @throws RuntimeException If the manager cannot be created
   */
  public static SecretManager create(String className) {
    return create(className, java.util.Collections.emptyMap());
  }

  /**
   * Gets a SecretManager by its reference key.
   *
   * @param referenceKey The secret reference key
   * @return The SecretManager for the given reference key
   */
  public static SecretManager get(String referenceKey) {
    return MANAGER_REGISTRY.get(referenceKey);
  }

  /**
   * Registers a SecretManager implementation.
   *
   * @param key The reference key for the manager
   * @param manager The manager to register
   */
  public static void register(String key, SecretManager manager) {
    MANAGER_REGISTRY.put(key, manager);
  }
}

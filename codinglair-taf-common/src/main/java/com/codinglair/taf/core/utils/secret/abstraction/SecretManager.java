package com.codinglair.taf.core.utils.secret.abstraction;

import com.codinglair.taf.core.validation.ValidationResult;

/**
 * Abstraction for secret management operations. Provides a SPI-based interface for different secret
 * management implementations.
 */
public interface SecretManager {

  /**
   * Resolves a secret by its reference key.
   *
   * @param referenceKey The secret reference key
   * @return The resolved secret value
   * @throws SecurityException If the secret cannot be resolved
   */
  String resolveSecret(String referenceKey);

  /**
   * Validates whether a secret reference is valid.
   *
   * @param referenceKey The secret reference key
   * @return Validation result for the secret reference
   */
  ValidationResult validateSecretReference(String referenceKey);

  /**
   * Decrypts a value using the configured decryption provider.
   *
   * @param encryptedValue The encrypted value to decrypt
   * @return The decrypted value
   */
  String decrypt(String encryptedValue);

  /**
   * Encrypts a value using the configured encryption provider.
   *
   * @param plainText The plain text value to encrypt
   * @return The encrypted value
   */
  String encrypt(String plainText);

  /**
   * Checks if a value is encrypted.
   *
   * @param value The value to check
   * @return true if the value is encrypted
   */
  boolean isEncrypted(String value);
}

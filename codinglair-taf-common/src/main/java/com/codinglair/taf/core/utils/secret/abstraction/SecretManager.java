package com.codinglair.taf.core.utils.secret.abstraction;

/**
 * An interface for secret manager implementation.
 */
public interface SecretManager {
    boolean isEncrypted(String value);
    String decrypt(String encryptedValue);
}


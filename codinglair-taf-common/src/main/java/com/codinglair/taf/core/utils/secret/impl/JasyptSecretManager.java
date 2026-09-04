package com.codinglair.taf.core.utils.secret.impl;

import com.codinglair.taf.core.utils.secret.abstraction.SecretManager;
import com.codinglair.taf.core.validation.ValidationResult;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.iv.RandomIvGenerator;
import org.jasypt.salt.RandomSaltGenerator;

/**
 * Legacy source-compatible Jasypt utility. New Runtime consumers use the Spring-managed {@code
 * com.codinglair.taf.runtime.secret.SecretManager} path.
 */
@Deprecated(forRemoval = true, since = "1.0")
public class JasyptSecretManager implements SecretManager {

  private final String jasyptPassword;

  public JasyptSecretManager(String jasyptPassword) {
    this.jasyptPassword = jasyptPassword;
  }

  @Override
  public String resolveSecret(String referenceKey) throws SecurityException {
    if (!isEncrypted(referenceKey)) {
      throw new SecurityException("Legacy secret value must use the ENC(...) form");
    }
    return decrypt(referenceKey);
  }

  @Override
  public String decrypt(String encryptedValue) {
    if (!isEncrypted(encryptedValue)) {
      throw new SecurityException("Legacy encrypted value is malformed");
    }
    try {
      return encryptor().decrypt(encryptedValue.substring(4, encryptedValue.length() - 1));
    } catch (RuntimeException failure) {
      throw new SecurityException("Legacy Jasypt decryption failed");
    }
  }

  @Override
  public String encrypt(String plainText) {
    if (plainText == null || plainText.isEmpty()) {
      throw new IllegalArgumentException("Plaintext must not be empty");
    }
    return "ENC(" + encryptor().encrypt(plainText) + ")";
  }

  @Override
  public ValidationResult validateSecretReference(String referenceKey) {
    if (!isEncrypted(referenceKey)) {
      return ValidationResult.failure(
          "Legacy secret reference is malformed",
          "Use ENC(...) or migrate to secret://jasypt/<payload>");
    }
    return ValidationResult.success(referenceKey, "Secret reference validated");
  }

  @Override
  public boolean isEncrypted(String value) {
    return value != null && value.startsWith("ENC(") && value.endsWith(")") && value.length() > 5;
  }

  private StandardPBEStringEncryptor encryptor() {
    if (jasyptPassword == null || jasyptPassword.isBlank()) {
      throw new SecurityException("Legacy Jasypt bootstrap credential is unavailable");
    }
    StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
    encryptor.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
    encryptor.setPassword(jasyptPassword);
    encryptor.setSaltGenerator(new RandomSaltGenerator());
    encryptor.setIvGenerator(new RandomIvGenerator());
    encryptor.setStringOutputType("base64");
    return encryptor;
  }
}

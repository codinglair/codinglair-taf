package com.codinglair.taf.runtime.secret;

import java.util.Objects;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.iv.RandomIvGenerator;
import org.jasypt.salt.RandomSaltGenerator;

public final class JasyptSecretProvider implements SecretProvider {
  public static final String ALGORITHM = "PBEWITHHMACSHA512ANDAES_256";
  private final EnvironmentValueSource environment;
  private final String masterKeyEnvironmentVariable;

  public JasyptSecretProvider(
      EnvironmentValueSource environment, String masterKeyEnvironmentVariable) {
    this.environment = Objects.requireNonNull(environment);
    if (masterKeyEnvironmentVariable == null
        || !masterKeyEnvironmentVariable.matches("[A-Z_][A-Z0-9_]{0,127}"))
      throw new IllegalArgumentException("Jasypt master-key environment-variable name is invalid");
    this.masterKeyEnvironmentVariable = masterKeyEnvironmentVariable;
  }

  @Override
  public String id() {
    return "jasypt";
  }

  @Override
  public void verifyReady() {
    masterKey();
  }

  @Override
  public ResolvedSecret resolve(SecretReference reference) {
    if (!id().equals(reference.provider()))
      throw new SecretResolutionException(
          SecretResolutionException.Category.MALFORMED_REFERENCE,
          "Reference does not select the Jasypt provider");
    try {
      return ResolvedSecret.of(
          encryptor(masterKey()).decrypt(reference.providerPayload()).toCharArray());
    } catch (SecretResolutionException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new SecretResolutionException(
          SecretResolutionException.Category.DECRYPTION_FAILED,
          "Jasypt credential decryption failed",
          failure);
    }
  }

  public String encryptForLocalDevelopment(char[] plaintext) {
    if (plaintext == null || plaintext.length == 0)
      throw new IllegalArgumentException("Plaintext must not be empty");
    return encryptor(masterKey()).encrypt(new String(plaintext));
  }

  private String masterKey() {
    String key = environment.get(masterKeyEnvironmentVariable);
    if (key == null || key.isBlank())
      throw new SecretResolutionException(
          SecretResolutionException.Category.PROVIDER_UNAVAILABLE,
          "Jasypt bootstrap credential is unavailable");
    return key;
  }

  private static StandardPBEStringEncryptor encryptor(String key) {
    StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
    encryptor.setAlgorithm(ALGORITHM);
    encryptor.setPassword(key);
    encryptor.setSaltGenerator(new RandomSaltGenerator());
    encryptor.setIvGenerator(new RandomIvGenerator());
    encryptor.setStringOutputType("base64");
    return encryptor;
  }
}

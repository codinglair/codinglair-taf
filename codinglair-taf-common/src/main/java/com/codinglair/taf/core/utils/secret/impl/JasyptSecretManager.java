package com.codinglair.taf.core.utils.secret.impl;

import com.codinglair.taf.core.utils.secret.abstraction.SecretManager;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;

/**
 * Jasypt Secret Manager implementation.
 */
public class JasyptSecretManager implements SecretManager {
    private  final StandardPBEStringEncryptor encryptor;

    public JasyptSecretManager(){
        this.encryptor = new StandardPBEStringEncryptor();
        // Set Jasypt master key in environment variable
        String masterKey = System.getenv("TEST_MASTER_KEY");
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException("CRITICAL: TAF_MASTER_KEY environment variable is not set!");
        }
        this.encryptor.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
        this.encryptor.setIvGenerator(new org.jasypt.iv.RandomIvGenerator());
        this.encryptor.setPassword(masterKey);
    }

    @Override
    public boolean isEncrypted(String value) {
        return (value != null && value.startsWith("ENC("));
    }

    public String decrypt(String encryptedValue) {
        // Make sure secret values are actually encrypted to avoid errors
        if (isEncrypted(encryptedValue)) {
            String cipher = encryptedValue.substring(4, encryptedValue.length() - 1);
            return encryptor.decrypt(cipher);
        }
        return encryptedValue;
    }
}

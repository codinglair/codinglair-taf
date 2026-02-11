package com.codinglair.taf.core.utils.secret.factory;

import com.codinglair.taf.core.utils.secret.abstraction.SecretManager;

public class SecretManagerFactory{
    public static <T extends SecretManager> T create(String secretManagerClassName) {
        try {
            // Dynamically loads the class and creates a new instance
            Class<?> clazz = Class.forName(secretManagerClassName);
            return (T) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize secret manager: " + secretManagerClassName, e);
        }
    }
}

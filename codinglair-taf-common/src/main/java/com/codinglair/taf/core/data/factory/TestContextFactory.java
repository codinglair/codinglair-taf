package com.codinglair.taf.core.data.factory;

import com.codinglair.taf.core.data.abstraction.TestContext;
import com.codinglair.taf.core.environment.EnvironmentProperties;

import java.lang.reflect.Constructor;

public class TestContextFactory {

    public static <T extends TestContext<?, ?>> T createContext(String contextClassName,
                                                                EnvironmentProperties props) {
        try {
            Class<?> clazz = Class.forName(contextClassName);
            Constructor<?> constructor = clazz.getDeclaredConstructor(EnvironmentProperties.class);
            return (T) constructor.newInstance(props);
        } catch(Exception e) {
            throw new RuntimeException("Failed to initialize context: " + contextClassName, e);
        }
    }
}

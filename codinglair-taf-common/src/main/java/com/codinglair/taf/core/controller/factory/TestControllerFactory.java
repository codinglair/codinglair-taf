package com.codinglair.taf.core.controller.factory;

import com.codinglair.taf.core.controller.abstraction.TestController;

public class TestControllerFactory {
    public static <T extends TestController> T create(String controllerClassName) {
        try {
            // Dynamically loads the class and creates a new instance
            Class<?> clazz = Class.forName(controllerClassName);
            return (T) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize controller: " + controllerClassName, e);
        }
    }
}

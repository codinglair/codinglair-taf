package com.codinglair.taf.core.reporting.factory;

import com.codinglair.taf.core.reporting.abstraction.TestReporter;

public class ReporterFactory {
    public static <T extends TestReporter> T create(String reporterClassName) {
        try {
            // Dynamically loads the class and creates a new instance
            Class<?> clazz = Class.forName(reporterClassName);
            return (T) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize reporter: " + reporterClassName, e);
        }
    }
}

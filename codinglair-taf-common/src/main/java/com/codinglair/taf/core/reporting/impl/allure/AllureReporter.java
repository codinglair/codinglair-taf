package com.codinglair.taf.core.reporting.impl.allure;

import com.codinglair.taf.core.reporting.abstraction.TestReporter;

import java.io.ByteArrayInputStream;

public class AllureReporter implements TestReporter {
    @Override
    public void setDescription(String description) {
        io.qameta.allure.Allure.description(description);
    }

    @Override
    public void logStep(String message) {
        io.qameta.allure.Allure.step(message);
    }

    @Override
    public void logInfo(String message) {
        io.qameta.allure.Allure.addAttachment("Info", message);
    }

    @Override
    public void attachScreenshot(String name, byte[] screenshot) {
        io.qameta.allure.Allure.addAttachment(name, new ByteArrayInputStream(screenshot));
    }

    @Override
    public void logError(String message, Throwable throwable) {
        // Allure automatically picks up thrown exceptions,
        // but this is useful for manual logging
        io.qameta.allure.Allure.step(message, io.qameta.allure.model.Status.FAILED);
    }
}
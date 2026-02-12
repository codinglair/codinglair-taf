package com.codinglair.taf.core.controller.impl;

import com.microsoft.playwright.options.LoadState;
import com.codinglair.taf.core.annotation.reporting.TafStep;
import com.codinglair.taf.core.controller.abstraction.TestController;

import com.microsoft.playwright.*;
import com.codinglair.taf.core.environment.EnvironmentProperties;
import io.qameta.allure.Allure;

import java.io.ByteArrayInputStream;

public class PlaywrightController implements TestController {
    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;

    @Override
    @TafStep("Initialize Playwright Engine")
    public void setup(EnvironmentProperties env) {
        this.playwright = Playwright.create();

        // Technical config from Environment Properties
        String browserName = env.getEnvProperty("browser.type");
        boolean headless = Boolean.parseBoolean(env.getEnvProperty("browser.headless"));
        double timeout = Double.parseDouble(env.getEnvProperty("browser.timeout"));

        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions().setHeadless(headless);

        this.browser = switch (browserName.toLowerCase()) {
            case "firefox" -> playwright.firefox().launch(launchOptions);
            case "webkit" -> playwright.webkit().launch(launchOptions);
            default -> playwright.chromium().launch(launchOptions);
        };

        this.context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1920, 1080));

        this.page = context.newPage();
        this.page.setDefaultTimeout(timeout);
    }

    @Override
    @TafStep("Close Playwright Engine")
    public void teardown() {
        if (page != null) page.close();
        if (context != null) context.close();
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    // --- Technical Browser Actions ---

    @TafStep("Navigate to URL: {url}")
    public void navigate(String url) {
        page.navigate(url);
    }

    public Page getPage() {
        return this.page;
    }

    @TafStep("Capture Screenshot: {name}")
    public void takeScreenshot(String name) {
        byte[] screenshot = page.screenshot(new Page.ScreenshotOptions()
                .setFullPage(true));
        Allure.addAttachment(name, new ByteArrayInputStream(screenshot));
    }

    /**
     * Technical utility to wait for network to be quiet.
     * Useful for pages with heavy JS/Ajax.
     */
    public void waitForIdle() {
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    @Override
    public byte[] getFailureAttachment() {
        return page.screenshot();
    }
    @Override
    public String getAttachmentType() { return "image/png"; }

}

package com.codinglair.taf.runtime.core.reporting.impl.allure.support;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;

@CucumberOptions(
    features = "classpath:features/rep005-publication.feature",
    glue = "com.codinglair.taf.runtime.core.reporting.impl.allure.support",
    plugin = "summary")
public final class Rep005CucumberRunner extends AbstractTestNGCucumberTests {}

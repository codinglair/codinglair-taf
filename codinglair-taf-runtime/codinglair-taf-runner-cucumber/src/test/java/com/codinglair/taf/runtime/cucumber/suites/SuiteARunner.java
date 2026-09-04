package com.codinglair.taf.runtime.cucumber.suites;

import com.codinglair.taf.runtime.cucumber.AbstractCucumberRunner;
import io.cucumber.testng.CucumberOptions;

@CucumberOptions(
    features = "classpath:features/suite_selection.feature",
    glue = "com.codinglair.taf.runtime.cucumber",
    tags = "@suite-a",
    plugin = "com.codinglair.taf.runtime.cucumber.CucumberBusinessReportPlugin")
public final class SuiteARunner extends AbstractCucumberRunner {}

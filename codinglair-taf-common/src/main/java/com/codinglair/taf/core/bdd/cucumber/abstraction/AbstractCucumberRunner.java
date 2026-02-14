package com.codinglair.taf.core.bdd.cucumber.abstraction;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import org.testng.annotations.DataProvider;

public abstract class AbstractCucumberRunner extends AbstractTestNGCucumberTests {

    @Override
    @DataProvider(parallel = true) // Allows BAs to see fast, parallel execution
    public Object[][] scenarios() {
        return super.scenarios();
    }
}

package com.codinglair.taf.runtime.cucumber;

import io.cucumber.testng.AbstractTestNGCucumberTests;

/**
 * Base runner for curated Cucumber features orchestrated by TestNG XML suites.
 *
 * <p>Consumer runner classes add {@code @CucumberOptions} and may then be selected by any TestNG
 * XML suite without changing Maven configuration.
 */
public abstract class AbstractCucumberRunner extends AbstractTestNGCucumberTests {}

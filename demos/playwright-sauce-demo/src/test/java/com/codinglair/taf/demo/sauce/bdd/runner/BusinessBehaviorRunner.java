package com.codinglair.taf.demo.sauce.bdd.runner;

import com.codinglair.taf.runtime.cucumber.AbstractCucumberRunner;
import io.cucumber.spring.SpringFactory;
import io.cucumber.testng.CucumberOptions;

@CucumberOptions(
    features = "classpath:features",
    glue = {"com.codinglair.taf.demo.sauce.bdd.steps", "com.codinglair.taf.runtime.cucumber"},
    objectFactory = SpringFactory.class,
    plugin = "com.codinglair.taf.runtime.cucumber.CucumberBusinessReportPlugin")
public final class BusinessBehaviorRunner extends AbstractCucumberRunner {}

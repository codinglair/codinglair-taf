package com.example.apple.bdd;

import com.codinglair.taf.runtime.cucumber.AbstractCucumberRunner;
import io.cucumber.testng.CucumberOptions;

@CucumberOptions(features = "classpath:features/apple.feature",
    glue = {"com.example.apple.bdd", "com.codinglair.taf.runtime.cucumber"},
    objectFactory = AppleObjectFactory.class)
public final class AppleBehaviorRunner extends AbstractCucumberRunner {}

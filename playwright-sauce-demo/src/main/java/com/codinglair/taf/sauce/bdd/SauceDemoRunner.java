package com.codinglair.taf.sauce.bdd;

import com.codinglair.taf.core.bdd.cucumber.abstraction.AbstractCucumberRunner;
import io.cucumber.testng.CucumberOptions;

@CucumberOptions(
        // IMPORTANT: Include the package of BaseTest in the glue!
        glue = {"com.codinglair.taf.core", "com.codinglair.taf.sauce.steps"},
        features = "src/main/resources/features",
        plugin = {"io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm"}
)
public class SauceDemoRunner extends AbstractCucumberRunner {
    // Zero code. It inherits everything.
}
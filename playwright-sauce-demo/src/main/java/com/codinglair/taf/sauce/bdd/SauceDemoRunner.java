package com.codinglair.taf.sauce.bdd;

import com.codinglair.taf.core.bdd.cucumber.abstraction.AbstractCucumberRunner;
import io.cucumber.testng.CucumberOptions;

@CucumberOptions(
        // IMPORTANT: Include the package of BaseTest in the glue!
        glue = {"classpath:com.codinglair.taf.core.test.abstraction",
                "com.codinglair.taf.sauce.bdd.steps"},
        features = "src/main/resources/features",
        plugin = {"io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm"}
)
public class SauceDemoRunner extends AbstractCucumberRunner {
    // Zero code. It inherits everything.
}
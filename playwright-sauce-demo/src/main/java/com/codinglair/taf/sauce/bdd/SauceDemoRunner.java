package com.codinglair.taf.sauce.bdd;

import com.codinglair.taf.core.test.bdd.cucumber.abstraction.AbstractCucumberRunner;
import io.cucumber.testng.CucumberOptions;

@CucumberOptions(
        glue = {"com.codinglair.taf.core.test.bdd.cucumber",
                "com.codinglair.taf.sauce.bdd.steps"},
        features = "src/main/resources/features",
        plugin = {"io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm"}
)
public class SauceDemoRunner extends AbstractCucumberRunner {

}
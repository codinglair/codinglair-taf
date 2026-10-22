package com.example.generated.bdd;

import com.codinglair.taf.runtime.cucumber.AbstractCucumberRunner;
import io.cucumber.testng.CucumberOptions;

@CucumberOptions(
    features = "classpath:features/generated-project.feature",
    glue = {"com.example.generated.bdd", "com.codinglair.taf.runtime.cucumber"})
public final class GeneratedCucumberRunner extends AbstractCucumberRunner {}


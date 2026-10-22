package com.codinglair.taf.qualification;

import com.codinglair.taf.runtime.cucumber.AbstractCucumberRunner;
import io.cucumber.testng.CucumberOptions;

@CucumberOptions(features = "classpath:features/evidence.feature", glue = {"com.codinglair.taf.qualification", "com.codinglair.taf.runtime.cucumber"})
public final class BusinessEvidenceRunner extends AbstractCucumberRunner {}

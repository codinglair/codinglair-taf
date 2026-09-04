package __BASE_PACKAGE__.bdd.runner;

import com.codinglair.taf.runtime.cucumber.AbstractCucumberRunner;
import io.cucumber.testng.CucumberOptions;

@CucumberOptions(
    features = "classpath:features",
    glue = {"__BASE_PACKAGE__.bdd.steps", "com.codinglair.taf.runtime.cucumber"},
    plugin = "com.codinglair.taf.runtime.cucumber.CucumberBusinessReportPlugin")
public final class BusinessBehaviorRunner extends AbstractCucumberRunner {}

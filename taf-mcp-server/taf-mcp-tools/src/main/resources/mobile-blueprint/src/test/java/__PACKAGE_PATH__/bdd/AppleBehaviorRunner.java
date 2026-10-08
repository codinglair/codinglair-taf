package __BASE_PACKAGE__.bdd;

import com.codinglair.taf.runtime.cucumber.AbstractCucumberRunner;
import io.cucumber.testng.CucumberOptions;

@CucumberOptions(features = "classpath:features/apple.feature",
    glue = {"__BASE_PACKAGE__.bdd", "com.codinglair.taf.runtime.cucumber"},
    objectFactory = AppleObjectFactory.class)
public final class AppleBehaviorRunner extends AbstractCucumberRunner {}

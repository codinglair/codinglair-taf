package __BASE_PACKAGE__.bdd;

import com.codinglair.taf.mobile.appium.configuration.AppleProperties;
import com.codinglair.taf.mobile.appium.service.AppleController;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflight;
import com.codinglair.taf.runtime.cucumber.CucumberScenarioSession;
import __BASE_PACKAGE__.configuration.InteractionProperties;
import __BASE_PACKAGE__.screen.apple.AppleScreen;
import __BASE_PACKAGE__.task.InteractionTask;
import io.cucumber.java.Before;
import io.cucumber.java.en.Then;
import org.springframework.context.ApplicationContext;

public final class AppleSteps {
  private final CucumberScenarioSession scenario;
  private final ApplicationContext context;

  public AppleSteps(CucumberScenarioSession scenario, ApplicationContext context) {
    this.scenario = scenario;
    this.context = context;
  }

  @Before(order = 0)
  public void verifyPrerequisites() { context.getBean(ConsumerPreflight.class).verify(); }

  @Then("the selected Apple interaction succeeds")
  public void verifyInteraction() {
    InteractionTask.verify(new AppleScreen(scenario.session().getController(AppleController.class, "primary"),
        context.getBean(InteractionProperties.class)), context.getBean(AppleProperties.class).settings("primary").getExecutionMode());
  }
}

package __BASE_PACKAGE__.bdd.steps;

import com.codinglair.taf.runtime.core.reporting.annotation.BddStep;
import __BASE_PACKAGE__.service.LoginWorkflow;
import io.cucumber.java.en.Given;

public final class ProductSteps {
  private final LoginWorkflow loginWorkflow;

  public ProductSteps(LoginWorkflow loginWorkflow) {
    this.loginWorkflow = loginWorkflow;
  }

  @Given("an authenticated shopper")
  @BddStep("Authenticate shopper")
  public void authenticate() {
    loginWorkflow.authenticate("REPLACE_ME", "secret://shop/standard-user");
  }
}

package __BASE_PACKAGE__.service;

import com.codinglair.taf.runtime.core.reporting.annotation.Workflow;
import __BASE_PACKAGE__.page.LoginPage;

public class LoginWorkflow {
  private final LoginPage loginPage;

  public LoginWorkflow(LoginPage loginPage) {
    this.loginPage = loginPage;
  }

  @Workflow("Authenticate prerequisite")
  public void authenticate(String username, String password) {
    loginPage.login(username, password);
  }
}

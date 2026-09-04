package com.codinglair.taf.demo.sauce.service;

import com.codinglair.taf.demo.sauce.page.LoginPage;
import com.codinglair.taf.runtime.core.reporting.annotation.Workflow;
import com.codinglair.taf.runtime.secret.ResolvedSecret;
import com.codinglair.taf.runtime.secret.SecretManager;
import com.codinglair.taf.runtime.secret.SecretRequestContext;

public class LoginWorkflow {
  private final LoginPage loginPage;
  private final SecretManager secrets;

  public LoginWorkflow(LoginPage loginPage, SecretManager secrets) {
    this.loginPage = loginPage;
    this.secrets = secrets;
  }

  @Workflow("Authenticate prerequisite")
  public void authenticate(String baseUrl, String username, String passwordReference) {
    loginPage.open(baseUrl);
    loginPage.enterUsername(username);
    SecretRequestContext request =
        new SecretRequestContext(
            "SauceDemoLoginWorkflow", "thread-" + Thread.currentThread().threadId(), "test", true);
    try (ResolvedSecret password = secrets.resolve(passwordReference, request)) {
      loginPage.enterPassword(password.useAsString());
    }
    loginPage.submit();
  }
}

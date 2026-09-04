package com.codinglair.taf.demo.sauce.functional;

import com.codinglair.taf.core.annotation.reporting.TafDescription;
import com.codinglair.taf.core.annotation.reporting.TestCaseId;
import com.codinglair.taf.demo.sauce.SauceDemoApplication;
import com.codinglair.taf.demo.sauce.configuration.SauceDemoProperties;
import com.codinglair.taf.demo.sauce.model.LoginInput;
import com.codinglair.taf.demo.sauce.page.ProductsPage;
import com.codinglair.taf.demo.sauce.service.LoginWorkflow;
import com.codinglair.taf.demo.sauce.validation.StringValidator;
import com.codinglair.taf.runtime.testng.TafBaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

@SpringBootTest(classes = SauceDemoApplication.class)
@ContextConfiguration(classes = SauceDemoApplication.class, inheritLocations = false)
public class SauceDemoLoginTest extends TafBaseTest {
  @Autowired LoginWorkflow login;
  @Autowired ProductsPage productsPage;
  @Autowired StringValidator validator;
  @Autowired SauceDemoProperties properties;

  @Test
  @TestCaseId("TC0001")
  @TafDescription("A standard user can log in to SauceDemo")
  public void successfulLogin() {
    LoginInput user = testInput(LoginInput.class);
    login.authenticate(properties.baseUrl(), user.username(), user.passwordReference());
    validator.validate("Products", productsPage.title());
  }
}

package com.codinglair.taf.demo.sauce.functional;

import com.codinglair.taf.core.annotation.reporting.TafDescription;
import com.codinglair.taf.core.annotation.reporting.TestCaseId;
import com.codinglair.taf.demo.sauce.SauceDemoApplication;
import com.codinglair.taf.demo.sauce.configuration.SauceDemoProperties;
import com.codinglair.taf.demo.sauce.model.LoginInput;
import com.codinglair.taf.demo.sauce.model.Product;
import com.codinglair.taf.demo.sauce.model.ProductExpectation;
import com.codinglair.taf.demo.sauce.page.ProductsPage;
import com.codinglair.taf.demo.sauce.service.LoginWorkflow;
import com.codinglair.taf.demo.sauce.validation.ProductValidator;
import com.codinglair.taf.runtime.testng.TafBaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

@SpringBootTest(classes = SauceDemoApplication.class)
@ContextConfiguration(classes = SauceDemoApplication.class, inheritLocations = false)
public class SauceDemoProductTest extends TafBaseTest {
  @Autowired LoginWorkflow loginWorkflow;
  @Autowired ProductsPage productsPage;
  @Autowired ProductValidator validator;
  @Autowired SauceDemoProperties properties;

  @Test
  @TestCaseId("TC0002")
  @TafDescription("Product details match the SauceDemo catalog")
  public void productDetailsMatchCatalog() {
    LoginInput user = testInput(LoginInput.class);
    ProductExpectation expected = expectedOutput(ProductExpectation.class);

    loginWorkflow.authenticate(properties.baseUrl(), user.username(), user.passwordReference());

    Product actual = productsPage.product(expected.productName());
    validator.validate(expected, actual);
  }
}

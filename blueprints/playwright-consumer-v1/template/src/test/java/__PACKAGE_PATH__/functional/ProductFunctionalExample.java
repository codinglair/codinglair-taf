package __BASE_PACKAGE__.functional;

import com.codinglair.taf.core.annotation.reporting.TestCaseId;
import com.codinglair.taf.runtime.testng.TafBaseTest;
import __BASE_PACKAGE__.model.LoginInput;
import __BASE_PACKAGE__.model.ProductExpectation;
import __BASE_PACKAGE__.page.LoginPage;
import __BASE_PACKAGE__.page.ProductsPage;
import __BASE_PACKAGE__.service.LoginWorkflow;
import __BASE_PACKAGE__.validation.ProductValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.Test;

public class ProductFunctionalExample extends TafBaseTest {
  @Autowired LoginWorkflow loginWorkflow;
  @Autowired LoginPage loginPage;
  @Autowired ProductsPage productsPage;
  @Autowired ProductValidator validator;

  @Test
  @TestCaseId("TC-PREREQUISITE")
  public void productAfterLoginPrerequisite() {
    LoginInput input = testInput(LoginInput.class);
    ProductExpectation expected = expectedOutput(ProductExpectation.class);
    loginWorkflow.authenticate(input.username(), input.passwordSecretReference());
    validator.matches(expected.productName(), productsPage.title());
  }

  @Test
  @TestCaseId("TC-LOGIN-SUBJECT")
  public void loginBehaviorIsExplicitSubject() {
    LoginInput input = testInput(LoginInput.class);
    loginPage.login(input.username(), input.passwordSecretReference());
  }
}

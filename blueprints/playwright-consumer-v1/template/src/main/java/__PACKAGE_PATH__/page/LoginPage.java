package __BASE_PACKAGE__.page;

import com.codinglair.taf.runtime.core.reporting.annotation.PageAction;
import com.codinglair.taf.web.playwright.PlaywrightObjectFactory;

public class LoginPage {
  private static final LocatorSpec USERNAME = new LocatorSpec("[data-test='username']");
  private static final LocatorSpec PASSWORD = new LocatorSpec("[data-test='password']");
  private static final LocatorSpec SUBMIT = new LocatorSpec("[data-test='login-button']");
  private final PlaywrightObjectFactory factory;

  public LoginPage(PlaywrightObjectFactory factory) {
    this.factory = factory;
  }

  @PageAction("Log in")
  public void login(String username, String password) {
    View view = view();
    view.username().fill(username);
    view.password().fill(password);
    view.submit().click();
  }

  private View view() {
    return factory.page("shop", LoginPage::createView);
  }

  private static View createView(com.microsoft.playwright.Page page) {
    return new View(USERNAME.resolve(page), PASSWORD.resolve(page), SUBMIT.resolve(page));
  }

  private record View(
      com.microsoft.playwright.Locator username,
      com.microsoft.playwright.Locator password,
      com.microsoft.playwright.Locator submit) {}
}

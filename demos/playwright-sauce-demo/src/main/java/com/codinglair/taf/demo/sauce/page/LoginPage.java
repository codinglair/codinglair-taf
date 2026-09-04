package com.codinglair.taf.demo.sauce.page;

import com.codinglair.taf.runtime.core.reporting.annotation.PageAction;
import com.codinglair.taf.web.playwright.PlaywrightObjectFactory;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

public class LoginPage {
  private static final LocatorSpec USERNAME = new LocatorSpec("[data-test='username']");
  private static final LocatorSpec PASSWORD = new LocatorSpec("[data-test='password']");
  private static final LocatorSpec SUBMIT = new LocatorSpec("[data-test='login-button']");
  private static final LocatorSpec ERROR = new LocatorSpec("[data-test='error']");
  private final PlaywrightObjectFactory factory;

  public LoginPage(PlaywrightObjectFactory factory) {
    this.factory = factory;
  }

  @PageAction("Open SauceDemo")
  public void open(String baseUrl) {
    view().open(baseUrl);
  }

  @PageAction("Enter username")
  public void enterUsername(String username) {
    view().username().fill(username);
  }

  @PageAction("Enter password")
  public void enterPassword(String password) {
    view().password().fill(password);
  }

  @PageAction("Submit login")
  public void submit() {
    view().submit().click();
  }

  @PageAction("Read login error")
  public String error() {
    return view().error().textContent();
  }

  private View view() {
    return factory.page("shop", LoginPage::createView);
  }

  private static View createView(Page page) {
    return new View(
        page,
        USERNAME.resolve(page),
        PASSWORD.resolve(page),
        SUBMIT.resolve(page),
        ERROR.resolve(page));
  }

  private record View(
      Page page, Locator username, Locator password, Locator submit, Locator error) {
    void open(String baseUrl) {
      page.navigate(baseUrl);
    }
  }
}

package com.codinglair.taf.demo.sauce.page;

import com.codinglair.taf.runtime.core.reporting.annotation.PageAction;
import com.codinglair.taf.web.playwright.PlaywrightObjectFactory;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

public class CheckoutPage {
  private static final LocatorSpec FIRST_NAME = new LocatorSpec("[data-test='firstName']");
  private static final LocatorSpec LAST_NAME = new LocatorSpec("[data-test='lastName']");
  private static final LocatorSpec POSTAL_CODE = new LocatorSpec("[data-test='postalCode']");
  private static final LocatorSpec CONTINUE = new LocatorSpec("[data-test='continue']");
  private static final LocatorSpec FINISH = new LocatorSpec("[data-test='finish']");
  private static final LocatorSpec COMPLETE_HEADER =
      new LocatorSpec("[data-test='complete-header']");
  private final PlaywrightObjectFactory factory;

  public CheckoutPage(PlaywrightObjectFactory factory) {
    this.factory = factory;
  }

  @PageAction("Enter checkout information")
  public void enterInformation(String firstName, String lastName, String postalCode) {
    factory.page("shop", CheckoutView::new).enterInformation(firstName, lastName, postalCode);
  }

  @PageAction("Finish checkout")
  public void finish() {
    factory.page("shop", CheckoutView::new).finish();
  }

  @PageAction("Read purchase confirmation")
  public String confirmation() {
    return factory.page("shop", CheckoutView::new).confirmation();
  }

  private static final class CheckoutView {
    private final Locator firstName;
    private final Locator lastName;
    private final Locator postalCode;
    private final Locator continueButton;
    private final Locator finish;
    private final Locator confirmation;

    private CheckoutView(Page page) {
      firstName = FIRST_NAME.resolve(page);
      lastName = LAST_NAME.resolve(page);
      postalCode = POSTAL_CODE.resolve(page);
      continueButton = CONTINUE.resolve(page);
      finish = FINISH.resolve(page);
      confirmation = COMPLETE_HEADER.resolve(page);
    }

    private void enterInformation(String first, String last, String postal) {
      firstName.fill(first);
      lastName.fill(last);
      postalCode.fill(postal);
      continueButton.click();
    }

    private void finish() {
      finish.click();
    }

    private String confirmation() {
      return confirmation.textContent();
    }
  }
}

package __BASE_PACKAGE__.page;

import com.codinglair.taf.runtime.core.reporting.annotation.PageAction;
import com.codinglair.taf.web.playwright.PlaywrightObjectFactory;

public class ProductsPage {
  private static final LocatorSpec TITLE = new LocatorSpec("[data-test='title']");
  private final PlaywrightObjectFactory factory;

  public ProductsPage(PlaywrightObjectFactory factory) {
    this.factory = factory;
  }

  @PageAction("Read products title")
  public String title() {
    return titleLocator().textContent();
  }

  private com.microsoft.playwright.Locator titleLocator() {
    return factory.page("shop", ProductsPage::createTitleLocator);
  }

  private static com.microsoft.playwright.Locator createTitleLocator(
      com.microsoft.playwright.Page page) {
    return TITLE.resolve(page);
  }
}

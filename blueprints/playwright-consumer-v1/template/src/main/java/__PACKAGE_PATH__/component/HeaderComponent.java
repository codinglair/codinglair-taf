package __BASE_PACKAGE__.component;

import com.codinglair.taf.runtime.core.reporting.annotation.ComponentAction;
import com.codinglair.taf.web.playwright.PlaywrightObjectFactory;
import __BASE_PACKAGE__.page.LocatorSpec;

/** PCOM is present because the header is reusable across multiple pages. */
public final class HeaderComponent {
  private static final LocatorSpec ROOT = new LocatorSpec("[data-test='primary-header']");
  private final PlaywrightObjectFactory factory;

  public HeaderComponent(PlaywrightObjectFactory factory) {
    this.factory = factory;
  }

  @ComponentAction("Read header")
  public String text() {
    return factory.component("shop", ROOT.value(), HeaderComponent::root).textContent();
  }

  private static com.microsoft.playwright.Locator root(
      com.microsoft.playwright.Page page, com.microsoft.playwright.Locator root) {
    return root;
  }
}

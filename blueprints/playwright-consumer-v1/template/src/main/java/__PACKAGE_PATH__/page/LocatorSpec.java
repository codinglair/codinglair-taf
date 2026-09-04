package __BASE_PACKAGE__.page;

public record LocatorSpec(String value) {
  public LocatorSpec {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("locator must not be blank");
  }

  com.microsoft.playwright.Locator resolve(com.microsoft.playwright.Page page) {
    return page.locator(value);
  }
}

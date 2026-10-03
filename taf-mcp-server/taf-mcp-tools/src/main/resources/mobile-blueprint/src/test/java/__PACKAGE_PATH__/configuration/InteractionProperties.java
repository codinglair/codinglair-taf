package __BASE_PACKAGE__.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("example.interaction")
public class InteractionProperties {
  private String accessibilityId = "";
  private String expectedText = "";
  private String webview = "";
  private String url = "";
  private String cssSelector = "";

  public String getAccessibilityId() { return accessibilityId; }
  public void setAccessibilityId(String value) { accessibilityId = value; }
  public String getExpectedText() { return expectedText; }
  public void setExpectedText(String value) { expectedText = value; }
  public String getWebview() { return webview; }
  public void setWebview(String value) { webview = value; }
  public String getUrl() { return url; }
  public void setUrl(String value) { url = value; }
  public String getCssSelector() { return cssSelector; }
  public void setCssSelector(String value) { cssSelector = value; }
}

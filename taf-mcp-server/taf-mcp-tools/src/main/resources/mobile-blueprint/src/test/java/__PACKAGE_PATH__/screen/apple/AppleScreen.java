package __BASE_PACKAGE__.screen.apple;

import com.codinglair.taf.mobile.appium.platform.AppleLocator;
import com.codinglair.taf.mobile.appium.service.AppleController;
import __BASE_PACKAGE__.configuration.InteractionProperties;

/** Session-local screen; element handles are resolved after every context mutation. */
public final class AppleScreen {
  private final AppleController controller;
  private final InteractionProperties interaction;
  private final AppleLocator nativeLocator;
  private final AppleLocator webLocator;

  public AppleScreen(AppleController controller, InteractionProperties interaction) {
    this.controller = controller;
    this.interaction = interaction;
    nativeLocator = interaction.getAccessibilityId().isBlank() ? null
        : new AppleLocator(AppleLocator.Kind.ACCESSIBILITY, interaction.getAccessibilityId());
    webLocator = interaction.getCssSelector().isBlank() ? null
        : new AppleLocator(AppleLocator.Kind.CSS, interaction.getCssSelector());
  }

  public AppleController controller() { return controller; }
  public InteractionProperties interaction() { return interaction; }

  public String tapAndRead() {
    if (nativeLocator == null) throw new IllegalStateException("Configure the native screen accessibility identifier");
    controller.tap(controller.find(nativeLocator));
    return controller.text(controller.find(nativeLocator));
  }

  public String webText() {
    if (webLocator == null) throw new IllegalStateException("Configure the web screen CSS selector");
    return controller.text(controller.find(webLocator));
  }
}

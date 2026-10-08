package __BASE_PACKAGE__.task;

import com.codinglair.taf.mobile.MobileExecutionMode;
import com.codinglair.taf.mobile.appium.service.AppleController;
import __BASE_PACKAGE__.configuration.InteractionProperties;
import __BASE_PACKAGE__.screen.apple.AppleScreen;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Deterministic consumer task contract checks; these do not qualify a real Apple target. */
public final class InteractionTaskTest {
  @DataProvider public Object[][] modes() {
    return new Object[][] {{MobileExecutionMode.NATIVE}, {MobileExecutionMode.HYBRID}, {MobileExecutionMode.SAFARI}};
  }

  @Test(dataProvider = "modes")
  public void sharesTaskAcrossModes(MobileExecutionMode mode) {
    var calls = new ArrayList<String>();
    var screen = screen(calls, "confirmed");
    InteractionTask.verify(screen, mode);
    switch (mode) {
      case NATIVE -> Assert.assertEquals(calls, java.util.List.of("find", "tap", "find", "text"));
      case HYBRID -> Assert.assertEquals(calls, java.util.List.of("selectWebView", "find", "text", "returnToNative"));
      case SAFARI -> Assert.assertEquals(calls, java.util.List.of("navigate", "find", "text"));
    }
  }

  @Test
  public void returnsToNativeWhenHybridReadFails() {
    var calls = new ArrayList<String>();
    Assert.expectThrows(IllegalStateException.class,
        () -> InteractionTask.verify(screen(calls, null), MobileExecutionMode.HYBRID));
    Assert.assertEquals(calls.getLast(), "returnToNative");
  }

  @Test
  public void rejectsUnexpectedInteractionResult() {
    Assert.expectThrows(AssertionError.class,
        () -> InteractionTask.verify(screen(new ArrayList<>(), "unexpected"), MobileExecutionMode.NATIVE));
  }

  private static AppleScreen screen(ArrayList<String> calls, String result) {
    var interaction = new InteractionProperties();
    interaction.setAccessibilityId("consumer-button");
    interaction.setExpectedText("confirmed");
    interaction.setWebview("WEBVIEW_consumer");
    interaction.setCssSelector("#result");
    interaction.setUrl("https://example.invalid/consumer");
    var controller = (AppleController) Proxy.newProxyInstance(AppleController.class.getClassLoader(),
        new Class<?>[] {AppleController.class}, (_, method, _) -> {
          calls.add(method.getName());
          if (method.getName().equals("text")) {
            if (result == null) throw new IllegalStateException("Consumer read failed");
            return result;
          }
          return null;
        });
    return new AppleScreen(controller, interaction);
  }
}

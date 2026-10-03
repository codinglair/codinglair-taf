package __BASE_PACKAGE__.task;

import com.codinglair.taf.mobile.MobileExecutionMode;
import __BASE_PACKAGE__.screen.apple.AppleScreen;

/** Reused by technical TestNG verification and curated Cucumber behavior. */
public final class InteractionTask {
  private InteractionTask() {}

  public static void verify(AppleScreen screen, MobileExecutionMode mode) {
    String actual = switch (mode) {
      case NATIVE -> screen.tapAndRead();
      case HYBRID -> {
        screen.controller().selectWebView(screen.interaction().getWebview());
        try { yield screen.webText(); }
        finally { screen.controller().returnToNative(); }
      }
      case SAFARI -> {
        screen.controller().navigate(screen.interaction().getUrl());
        yield screen.webText();
      }
    };
    if (!screen.interaction().getExpectedText().equals(actual))
      throw new AssertionError("Selected Apple interaction returned unexpected text");
  }
}

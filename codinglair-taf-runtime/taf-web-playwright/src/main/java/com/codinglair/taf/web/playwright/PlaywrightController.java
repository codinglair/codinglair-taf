package com.codinglair.taf.web.playwright;

import com.codinglair.taf.runtime.core.controller.TestController;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Public Playwright capability. Native handles are intentional, capability-local escape hatches.
 */
public interface PlaywrightController extends TestController {
  Page page();

  BrowserContext browserContext();

  Browser browser();

  void navigate(String url);

  Locator locator(String selector);

  Locator byRole(AriaRole role, String name);

  Locator byText(String text);

  Locator byLabel(String label);

  Locator byTestId(String testId);

  FrameLocator frame(String selector);

  void click(Locator locator);

  void fill(Locator locator, String text);

  void type(Locator locator, String text);

  void check(Locator locator);

  void uncheck(Locator locator);

  void selectOption(Locator locator, String option);

  void press(Locator locator, String key);

  void hover(Locator locator);

  void rightClick(Locator locator);

  void doubleClick(Locator locator);

  void dragAndDrop(Locator source, Locator target);

  void upload(Locator locator, Path file);

  Download download(Locator locator, Path target);

  Page waitForPopup(Runnable trigger);

  List<Page> pages();

  void onDialog(Consumer<Dialog> handler);

  void route(String urlPattern, Consumer<Route> handler);

  List<Request> requests();

  void saveStorageState(Path target);

  byte[] screenshot();

  void screenshot(Path target);

  void waitUntil(Locator locator, WaitForSelectorState state);

  void waitUntil(Predicate<Page> predicate);

  void expectVisible(Locator locator);

  void expectText(Locator locator, String text);
}

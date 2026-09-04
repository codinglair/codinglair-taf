package com.codinglair.taf.web.playwright;

import com.codinglair.taf.runtime.core.lifecycle.SessionAwareAccessor;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Singleton-safe factory for invocation-owned page and page-component objects. */
public final class PlaywrightObjectFactory {
  private final SessionAwareAccessor sessions;

  public PlaywrightObjectFactory(SessionAwareAccessor sessions) {
    this.sessions = Objects.requireNonNull(sessions, "sessions");
  }

  public <T> T page(String controllerName, Function<Page, T> constructor) {
    Objects.requireNonNull(constructor, "constructor");
    return constructor.apply(controller(controllerName).page());
  }

  public <T> T component(
      String controllerName, String selector, BiFunction<Page, Locator, T> constructor) {
    Objects.requireNonNull(selector, "selector");
    Objects.requireNonNull(constructor, "constructor");
    PlaywrightController controller = controller(controllerName);
    Page page = controller.page();
    return constructor.apply(page, page.locator(selector));
  }

  private PlaywrightController controller(String name) {
    return sessions.controller(PlaywrightController.class, name);
  }
}

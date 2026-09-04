package com.codinglair.taf.web.playwright;

/** Creates a fresh controller for registration in one TestSession. */
@FunctionalInterface
public interface PlaywrightControllerFactory {
  PlaywrightController create(String name);
}

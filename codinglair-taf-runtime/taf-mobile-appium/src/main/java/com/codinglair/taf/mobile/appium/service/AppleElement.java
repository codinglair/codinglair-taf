package com.codinglair.taf.mobile.appium.service;

import org.openqa.selenium.WebElement;

/** Opaque session/context-bound handle. Resolve again after context or application changes. */
public final class AppleElement {
  final DefaultAppleController owner;
  final long generation;
  final WebElement element;

  AppleElement(DefaultAppleController owner, long generation, WebElement element) {
    this.owner = owner;
    this.generation = generation;
    this.element = element;
  }
}

package com.codinglair.taf.demo.sauce.context;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codinglair.taf.demo.sauce.SauceDemoApplication;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflight;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflightException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = SauceDemoApplication.class,
    properties = "taf.web.playwright.controllers.shop.base-url=REPLACE_ME")
class PreflightTest {
  @Autowired ConsumerPreflight preflight;

  @org.junit.jupiter.api.Test
  void unresolvedRequiredValuesFailBeforeBrowserStartup() {
    ConsumerPreflightException failure =
        assertThrows(ConsumerPreflightException.class, preflight::verify);

    assertTrue(
        failure.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.checkId().equals("web-playwright.shop")));
  }
}

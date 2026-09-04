package com.codinglair.taf.smoke;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.codinglair.taf.web.playwright.PlaywrightController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Playwright staged consumer")
class WebCapabilitySmokeTest {
  @Test
  @DisplayName("resolves the optional capability without internal fixtures")
  void resolvesTheOptionalCapabilityWithoutInternalFixtures() {
    assertNotNull(PlaywrightController.class);
  }
}

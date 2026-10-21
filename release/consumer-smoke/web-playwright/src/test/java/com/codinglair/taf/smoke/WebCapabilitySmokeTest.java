package com.codinglair.taf.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.web.playwright.PlaywrightController;
import org.testng.annotations.Test;
class WebCapabilitySmokeTest extends com.codinglair.taf.runtime.testng.TafBaseTest {
  @Test
  void resolvesTheOptionalCapabilityWithoutInternalFixtures() {
    assertThat(PlaywrightController.class).isNotNull();
  }
}

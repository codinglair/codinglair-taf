package com.codinglair.taf.web.playwright;

import static org.assertj.core.api.Assertions.*;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class PlaywrightPropertiesTest {
  @Test
  void remoteModeRequiresEndpoint() {
    PlaywrightProperties properties = new PlaywrightProperties();
    properties.setMode(PlaywrightProperties.Mode.REMOTE);
    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("remote-endpoint");
  }

  @Test
  void remoteEndpointMustBeWebSocketAndAbsolute() {
    PlaywrightProperties properties = new PlaywrightProperties();
    properties.setMode(PlaywrightProperties.Mode.REMOTE);
    properties.setRemoteEndpoint(URI.create("https://token=do-not-repeat@example.test/playwright"));
    assertThatThrownBy(properties::validate)
        .hasMessageContaining("ws://")
        .hasMessageNotContaining("do-not-repeat");
  }

  @Test
  void chromiumAcceptsChromeAndEdgeChannels() {
    for (String channel : new String[] {"chrome", "msedge"}) {
      PlaywrightProperties properties = new PlaywrightProperties();
      properties.setChannel(channel);
      assertThatCode(properties::validate).doesNotThrowAnyException();
    }
  }

  @Test
  void nonChromiumRejectsChannelsAndInvalidTimeouts() {
    PlaywrightProperties properties = new PlaywrightProperties();
    properties.setEngine(PlaywrightProperties.Engine.FIREFOX);
    properties.setChannel("chrome");
    assertThatThrownBy(properties::validate).hasMessageContaining("Chromium");
    properties.setChannel(null);
    properties.setTimeout(Duration.ZERO);
    assertThatThrownBy(properties::validate).hasMessageContaining("positive");
  }

  @Test
  void namedControllerSettingsAreTypedAndValidatedIndependently() {
    PlaywrightProperties properties = new PlaywrightProperties();
    PlaywrightControllerSettings customer = new PlaywrightControllerSettings();
    customer.setBaseUrl(URI.create("https://example.test/app/"));
    properties.getControllers().put("customer", customer);
    assertThat(properties.settings("customer")).isSameAs(customer);
    assertThatCode(properties::validate).doesNotThrowAnyException();

    customer.setBaseUrl(URI.create("file:///sensitive/path"));
    assertThatThrownBy(properties::validate)
        .hasMessageContaining("controllers.customer.base-url")
        .hasMessageNotContaining("sensitive");
  }
}

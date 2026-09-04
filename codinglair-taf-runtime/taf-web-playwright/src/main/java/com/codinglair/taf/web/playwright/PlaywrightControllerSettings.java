package com.codinglair.taf.web.playwright;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

/** Settings for one named Playwright controller instance. */
public class PlaywrightControllerSettings {
  private PlaywrightProperties.Engine engine = PlaywrightProperties.Engine.CHROMIUM;
  private PlaywrightProperties.Mode mode = PlaywrightProperties.Mode.LOCAL;
  private String channel;
  private URI baseUrl;
  private URI remoteEndpoint;
  private boolean headless = true;
  private Duration timeout = Duration.ofSeconds(30);
  private int viewportWidth = 1920;
  private int viewportHeight = 1080;
  private Path downloadsDirectory;
  private Path storageState;
  private final PlaywrightProperties.Evidence evidence = new PlaywrightProperties.Evidence();

  public void validate(String propertyPrefix) {
    if (timeout == null || timeout.isZero() || timeout.isNegative())
      throw new IllegalArgumentException(propertyPrefix + ".timeout must be positive");
    if (viewportWidth < 1 || viewportHeight < 1)
      throw new IllegalArgumentException(propertyPrefix + " viewport dimensions must be positive");
    if (baseUrl != null
        && (!baseUrl.isAbsolute()
            || !("http".equalsIgnoreCase(baseUrl.getScheme())
                || "https".equalsIgnoreCase(baseUrl.getScheme())))) {
      throw new IllegalArgumentException(
          propertyPrefix + ".base-url must be an absolute http:// or https:// URI");
    }
    if (mode == PlaywrightProperties.Mode.REMOTE) {
      if (remoteEndpoint == null)
        throw new IllegalArgumentException(
            propertyPrefix + ".remote-endpoint is required in REMOTE mode");
      String scheme = remoteEndpoint.getScheme();
      if (scheme == null
          || !(scheme.equalsIgnoreCase("ws") || scheme.equalsIgnoreCase("wss"))
          || remoteEndpoint.getHost() == null) {
        throw new IllegalArgumentException(
            propertyPrefix + ".remote-endpoint must be an absolute ws:// or wss:// URI");
      }
    }
    if (channel != null && !channel.isBlank() && engine != PlaywrightProperties.Engine.CHROMIUM)
      throw new IllegalArgumentException(propertyPrefix + ".channel is supported only by Chromium");
  }

  public PlaywrightProperties.Engine getEngine() {
    return engine;
  }

  public void setEngine(PlaywrightProperties.Engine engine) {
    this.engine = engine;
  }

  public PlaywrightProperties.Mode getMode() {
    return mode;
  }

  public void setMode(PlaywrightProperties.Mode mode) {
    this.mode = mode;
  }

  public String getChannel() {
    return channel;
  }

  public void setChannel(String channel) {
    this.channel = channel;
  }

  public URI getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(URI baseUrl) {
    this.baseUrl = baseUrl;
  }

  public URI getRemoteEndpoint() {
    return remoteEndpoint;
  }

  public void setRemoteEndpoint(URI remoteEndpoint) {
    this.remoteEndpoint = remoteEndpoint;
  }

  public boolean isHeadless() {
    return headless;
  }

  public void setHeadless(boolean headless) {
    this.headless = headless;
  }

  public Duration getTimeout() {
    return timeout;
  }

  public void setTimeout(Duration timeout) {
    this.timeout = timeout;
  }

  public int getViewportWidth() {
    return viewportWidth;
  }

  public void setViewportWidth(int viewportWidth) {
    this.viewportWidth = viewportWidth;
  }

  public int getViewportHeight() {
    return viewportHeight;
  }

  public void setViewportHeight(int viewportHeight) {
    this.viewportHeight = viewportHeight;
  }

  public Path getDownloadsDirectory() {
    return downloadsDirectory;
  }

  public void setDownloadsDirectory(Path downloadsDirectory) {
    this.downloadsDirectory = downloadsDirectory;
  }

  public Path getStorageState() {
    return storageState;
  }

  public void setStorageState(Path storageState) {
    this.storageState = storageState;
  }

  public PlaywrightProperties.Evidence getEvidence() {
    return evidence;
  }
}

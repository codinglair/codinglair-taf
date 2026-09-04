package com.codinglair.taf.api.rest;

import java.net.URI;
import java.time.Duration;

/** Settings for one named REST controller. */
public class RestControllerSettings {
  private URI baseUrl;
  private Duration timeout = Duration.ofSeconds(30);

  public void validate(String prefix) {
    if (baseUrl == null
        || !baseUrl.isAbsolute()
        || baseUrl.getHost() == null
        || !("http".equalsIgnoreCase(baseUrl.getScheme())
            || "https".equalsIgnoreCase(baseUrl.getScheme())))
      throw new IllegalArgumentException(prefix + ".base-url must be an absolute HTTP(S) URI");
    if (timeout == null || timeout.isZero() || timeout.isNegative())
      throw new IllegalArgumentException(prefix + ".timeout must be positive");
  }

  public URI getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(URI value) {
    baseUrl = value;
  }

  public Duration getTimeout() {
    return timeout;
  }

  public void setTimeout(Duration value) {
    timeout = value;
  }
}

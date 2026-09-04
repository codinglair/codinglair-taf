package com.codinglair.taf.api.soap;

import java.net.URI;
import java.time.Duration;

public class SoapControllerSettings {
  private URI endpoint;
  private Duration timeout = Duration.ofSeconds(30);
  private long maxResponseBytes = 10 * 1024 * 1024;

  public void validate(String prefix) {
    if (endpoint == null
        || !endpoint.isAbsolute()
        || endpoint.getHost() == null
        || !("http".equalsIgnoreCase(endpoint.getScheme())
            || "https".equalsIgnoreCase(endpoint.getScheme())))
      throw new IllegalArgumentException(prefix + ".endpoint must be an absolute HTTP(S) URI");
    if (timeout == null || timeout.isZero() || timeout.isNegative())
      throw new IllegalArgumentException(prefix + ".timeout must be positive");
    if (maxResponseBytes < 1)
      throw new IllegalArgumentException(prefix + ".max-response-bytes must be positive");
  }

  public URI getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(URI value) {
    endpoint = value;
  }

  public Duration getTimeout() {
    return timeout;
  }

  public void setTimeout(Duration value) {
    timeout = value;
  }

  public long getMaxResponseBytes() {
    return maxResponseBytes;
  }

  public void setMaxResponseBytes(long value) {
    maxResponseBytes = value;
  }
}

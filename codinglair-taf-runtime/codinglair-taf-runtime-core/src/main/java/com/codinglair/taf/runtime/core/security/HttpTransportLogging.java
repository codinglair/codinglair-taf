package com.codinglair.taf.runtime.core.security;

/** JDK wire diagnostics bypass application redaction and cannot retain credential-safe payloads. */
public final class HttpTransportLogging {
  private HttpTransportLogging() {}

  public static void requireSafe() {
    if (!System.getProperty("jdk.httpclient.HttpClient.log", "").isBlank())
      throw new IllegalStateException("Disable JDK HTTP wire logging for protected transport");
  }
}

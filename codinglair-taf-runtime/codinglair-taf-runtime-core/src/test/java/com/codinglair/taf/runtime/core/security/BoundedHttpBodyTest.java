package com.codinglair.taf.runtime.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Streaming HTTP body limits and cancellation cleanup")
class BoundedHttpBodyTest {
  @Test
  @DisplayName("returns bounded content and rejects an oversized response")
  void bytes() throws Exception {
    assertThat(
            BoundedHttpBody.read(
                new ByteArrayInputStream(new byte[] {1, 2}), 2, Duration.ofSeconds(1)))
        .containsExactly(1, 2);
    assertThrows(
        IOException.class,
        () ->
            BoundedHttpBody.read(new ByteArrayInputStream(new byte[3]), 2, Duration.ofSeconds(1)));
  }

  @Test
  @DisplayName("closes a stalled stream on deadline without leaving a blocked reader")
  void deadline() {
    var closed = new CountDownLatch(1);
    InputStream stalled =
        new InputStream() {
          @Override
          public int read() throws IOException {
            try {
              closed.await();
              return -1;
            } catch (InterruptedException _) {
              Thread.currentThread().interrupt();
              throw new IOException();
            }
          }

          @Override
          public void close() {
            closed.countDown();
          }
        };
    assertTimeoutPreemptively(
        Duration.ofSeconds(2),
        () ->
            assertThrows(
                IOException.class,
                () -> BoundedHttpBody.read(stalled, 10, Duration.ofMillis(100))));
    assertThat(closed.getCount()).isZero();
  }

  @Test
  @DisplayName("unsafe JDK HTTP wire diagnostics fail closed before transport construction")
  void wireLogging() {
    String previous = System.getProperty("jdk.httpclient.HttpClient.log");
    try {
      System.setProperty("jdk.httpclient.HttpClient.log", "headers,content");
      assertThrows(IllegalStateException.class, HttpTransportLogging::requireSafe);
    } finally {
      if (previous == null) System.clearProperty("jdk.httpclient.HttpClient.log");
      else System.setProperty("jdk.httpclient.HttpClient.log", previous);
    }
  }
}

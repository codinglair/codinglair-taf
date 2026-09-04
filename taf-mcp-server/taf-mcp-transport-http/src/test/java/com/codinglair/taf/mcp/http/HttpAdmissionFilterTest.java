package com.codinglair.taf.mcp.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("Streamable HTTP admission limits")
class HttpAdmissionFilterTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC);

  @Nested
  @DisplayName("request bounds")
  class RequestBounds {
    @Test
    @DisplayName("rejects an oversized MCP body before the handler")
    void rejectsOversizedBody() throws Exception {
      TafMcpHttpProperties properties = properties();
      properties.setMaxRequestBytes(1024);
      var filter = new HttpAdmissionFilter(properties, CLOCK);
      var request = request();
      request.setContent(new byte[1025]);
      var response = new MockHttpServletResponse();

      filter.doFilter(request, response, new MockFilterChain());

      assertThat(response.getStatus()).isEqualTo(413);
    }

    @Test
    @DisplayName("rate limits requests without sharing mutable counters unsafely")
    void rateLimitsRequests() throws Exception {
      TafMcpHttpProperties properties = properties();
      properties.setRequestsPerMinute(1);
      var filter = new HttpAdmissionFilter(properties, CLOCK);
      filter.doFilter(request(), new MockHttpServletResponse(), new MockFilterChain());
      var rejected = new MockHttpServletResponse();

      filter.doFilter(request(), rejected, new MockFilterChain());

      assertThat(rejected.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("limits distinct concurrent sessions and releases deleted sessions")
    void limitsSessions() throws Exception {
      TafMcpHttpProperties properties = properties();
      properties.setMaxSessions(1);
      var filter = new HttpAdmissionFilter(properties, CLOCK);
      filter.doFilter(session("one", "POST"), new MockHttpServletResponse(), new MockFilterChain());
      var rejected = new MockHttpServletResponse();
      filter.doFilter(session("two", "POST"), rejected, new MockFilterChain());
      filter.doFilter(
          session("one", "DELETE"), new MockHttpServletResponse(), new MockFilterChain());
      var accepted = new MockHttpServletResponse();
      filter.doFilter(session("two", "POST"), accepted, new MockFilterChain());

      assertThat(rejected.getStatus()).isEqualTo(429);
      assertThat(accepted.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("rejects excess concurrent requests without blocking")
    void limitsConcurrentRequests() throws Exception {
      TafMcpHttpProperties properties = properties();
      properties.setMaxConcurrentRequests(1);
      var filter = new HttpAdmissionFilter(properties, CLOCK);
      var entered = new CountDownLatch(1);
      var release = new CountDownLatch(1);
      Thread first =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try {
                      filter.doFilter(
                          request(),
                          new MockHttpServletResponse(),
                          (request, response) -> {
                            entered.countDown();
                            try {
                              release.await();
                            } catch (InterruptedException interrupted) {
                              Thread.currentThread().interrupt();
                              throw new java.io.IOException("interrupted", interrupted);
                            }
                          });
                    } catch (Exception failure) {
                      throw new AssertionError(failure);
                    }
                  });
      assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
      var rejected = new MockHttpServletResponse();

      filter.doFilter(request(), rejected, new MockFilterChain());
      release.countDown();
      first.join();

      assertThat(rejected.getStatus()).isEqualTo(503);
    }
  }

  private static TafMcpHttpProperties properties() {
    TafMcpHttpProperties result = new TafMcpHttpProperties();
    result.setRequestsPerMinute(100);
    return result;
  }

  private static MockHttpServletRequest request() {
    return session(null, "POST");
  }

  private static MockHttpServletRequest session(String id, String method) {
    var request = new MockHttpServletRequest(method, "/mcp");
    request.setRemoteAddr("192.0.2.10");
    if (id != null) request.addHeader("Mcp-Session-Id", id);
    return request;
  }
}

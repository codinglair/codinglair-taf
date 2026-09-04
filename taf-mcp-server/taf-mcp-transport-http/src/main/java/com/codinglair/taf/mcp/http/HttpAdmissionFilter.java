package com.codinglair.taf.mcp.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.filter.OncePerRequestFilter;

/** Thread-safe, fail-closed bounds applied before MCP request parsing. */
final class HttpAdmissionFilter extends OncePerRequestFilter {
  private static final String SESSION_HEADER = "Mcp-Session-Id";
  private final TafMcpHttpProperties limits;
  private final Semaphore requests;
  private final Set<String> sessions = ConcurrentHashMap.newKeySet();
  private final ConcurrentHashMap<String, RateWindow> rates = new ConcurrentHashMap<>();
  private final Clock clock;

  HttpAdmissionFilter(TafMcpHttpProperties limits, Clock clock) {
    this.limits = limits;
    this.requests = new Semaphore(limits.getMaxConcurrentRequests());
    this.clock = clock;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().equals("/mcp");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getContentLengthLong() > limits.getMaxRequestBytes()) {
      reject(response, 413);
      return;
    }
    if (!requests.tryAcquire()) {
      reject(response, 503);
      return;
    }
    try {
      String key = request.getRemoteAddr();
      if (!allowRate(key)) {
        reject(response, 429);
        return;
      }
      String session = request.getHeader(SESSION_HEADER);
      if (session != null && !session.isBlank() && !sessions.contains(session)) {
        if (sessions.size() >= limits.getMaxSessions()) {
          reject(response, 429);
          return;
        }
        sessions.add(session);
      }
      response.setHeader(
          "X-TAF-Request-Deadline-Millis",
          Long.toString(clock.millis() + limits.getRequestTimeout().toMillis()));
      chain.doFilter(request, response);
      if ("DELETE".equals(request.getMethod()) && session != null) sessions.remove(session);
    } finally {
      requests.release();
    }
  }

  private boolean allowRate(String key) {
    long minute = clock.millis() / 60_000;
    RateWindow current =
        rates.compute(
            key,
            (_, prior) -> prior == null || prior.minute != minute ? new RateWindow(minute) : prior);
    return current.count.incrementAndGet() <= limits.getRequestsPerMinute();
  }

  private static void reject(HttpServletResponse response, int status) throws IOException {
    response.sendError(status);
  }

  private static final class RateWindow {
    private final long minute;
    private final AtomicInteger count = new AtomicInteger();

    private RateWindow(long minute) {
      this.minute = minute;
    }
  }
}

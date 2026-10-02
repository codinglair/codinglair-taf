package com.codinglair.taf.runtime.core.security;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Bounded downloads; callers apply evidence content filtering before retaining returned bytes. */
public final class ArtifactDownloadTransport {
  private final ResourceAuthorizer authorizer;
  private final Duration timeout;
  private final int maximumBytes;
  private final int maximumRedirects;

  public ArtifactDownloadTransport(
      ResourceAuthorizer authorizer, Duration timeout, int maximumBytes, int maximumRedirects) {
    this.authorizer = Objects.requireNonNull(authorizer);
    if (timeout == null
        || timeout.isNegative()
        || timeout.isZero()
        || timeout.compareTo(Duration.ofMinutes(10)) > 0
        || maximumBytes < 1
        || maximumBytes > 64 * 1024 * 1024
        || maximumRedirects < 0
        || maximumRedirects > 5)
      throw new IllegalArgumentException("Invalid artifact download limits");
    this.timeout = timeout;
    this.maximumBytes = maximumBytes;
    this.maximumRedirects = maximumRedirects;
  }

  /** Query signatures are used on the wire only; policy identifies an exact origin and path. */
  public static String destination(URI uri) {
    validate(uri);
    return origin(uri) + (uri.getRawPath().isEmpty() ? "/" : uri.getRawPath());
  }

  public static String origin(URI uri) {
    validate(uri);
    int port =
        uri.getPort() == -1
            ? (uri.getScheme().equalsIgnoreCase("https") ? 443 : 80)
            : uri.getPort();
    return uri.getScheme().toLowerCase(Locale.ROOT)
        + "://"
        + uri.getHost().toLowerCase(Locale.ROOT)
        + ":"
        + port;
  }

  private static void validate(URI uri) {
    if (uri == null
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getFragment() != null
        || uri.getPort() > 65535
        || uri.toASCIIString().length() > 16384
        || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
        || !uri.normalize().equals(uri)
        || uri.getRawPath().matches("(?i).*%(?:2e|2f|5c|25|0[0-9a-f]|1[0-9a-f]).*"))
      throw new IllegalArgumentException("Invalid artifact destination");
  }

  public byte[] download(URI initial, Map<String, String> credentialHeaders) {
    HttpTransportLogging.requireSafe();
    var headers = Map.copyOf(credentialHeaders);
    URI current = initial;
    long deadline = System.nanoTime() + timeout.toNanos();
    try (var client =
        HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()) {
      for (int redirects = 0; ; redirects++) {
        authorizer.require(
            new ResourceAccess(
                ResourceAccess.Kind.ARTIFACT_DESTINATION, destination(current), "download"));
        boolean forward = origin(initial).equals(origin(current));
        if (!forward && !headers.isEmpty())
          forward =
              authorizer.permits(
                  new ResourceAccess(
                      ResourceAccess.Kind.CREDENTIAL_FORWARDING,
                      origin(initial) + " -> " + origin(current),
                      "forward"));
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) throw new IOException();
        var request = HttpRequest.newBuilder(current).timeout(Duration.ofNanos(remaining)).GET();
        if (forward) headers.forEach(request::header);
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
          if (Set.of(301, 302, 303, 307, 308).contains(response.statusCode())) {
            if (redirects >= maximumRedirects) throw new IOException();
            String location =
                response.headers().firstValue("Location").orElseThrow(IOException::new);
            URI next = current.resolve(location);
            validate(next);
            if (current.getScheme().equalsIgnoreCase("https")
                && !next.getScheme().equalsIgnoreCase("https"))
              throw new SecurityException("Artifact TLS downgrade denied");
            current = next;
            continue;
          }
          if (response.statusCode() != 200) throw new IOException();
          long bodyRemaining = deadline - System.nanoTime();
          if (bodyRemaining <= 0) throw new IOException();
          byte[] bytes = BoundedHttpBody.read(body, maximumBytes, Duration.ofNanos(bodyRemaining));
          return bytes;
        }
      }
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Artifact download cancelled");
    } catch (IOException | IllegalArgumentException _) {
      throw new IllegalStateException("Artifact download failed safely");
    }
  }
}

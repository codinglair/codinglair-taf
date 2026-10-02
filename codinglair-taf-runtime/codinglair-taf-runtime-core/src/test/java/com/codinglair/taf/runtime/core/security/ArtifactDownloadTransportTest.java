package com.codinglair.taf.runtime.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Artifact destination authorization and bounded redirect transport")
class ArtifactDownloadTransportTest {
  HttpServer source;
  HttpServer target;
  URI first;
  URI second;
  final AtomicInteger sourceConnections = new AtomicInteger();
  final AtomicInteger targetConnections = new AtomicInteger();
  final AtomicReference<String> receivedCredential = new AtomicReference<>();
  String redirect;

  @BeforeEach
  void start() throws Exception {
    source = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    target = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    first =
        URI.create(
            "http://127.0.0.1:"
                + source.getAddress().getPort()
                + "/artifact?signature=FAKE_SIGNED_CANARY");
    second =
        URI.create(
            "http://127.0.0.1:"
                + target.getAddress().getPort()
                + "/artifact?token=FAKE_SIGNED_CANARY");
    source.createContext(
        "/artifact",
        exchange -> {
          sourceConnections.incrementAndGet();
          if (redirect != null) {
            exchange.getResponseHeaders().set("Location", redirect);
            exchange.sendResponseHeaders(302, -1);
          } else {
            exchange.sendResponseHeaders(200, 8);
            exchange.getResponseBody().write("artifact".getBytes(StandardCharsets.UTF_8));
          }
          exchange.close();
        });
    target.createContext(
        "/artifact",
        exchange -> {
          targetConnections.incrementAndGet();
          receivedCredential.set(exchange.getRequestHeaders().getFirst("Authorization"));
          exchange.sendResponseHeaders(200, 8);
          exchange.getResponseBody().write("artifact".getBytes(StandardCharsets.UTF_8));
          exchange.close();
        });
    source.start();
    target.start();
  }

  @AfterEach
  void stop() {
    source.stop(0);
    target.stop(0);
  }

  ResourceAccess grant(URI uri) {
    return new ResourceAccess(
        ResourceAccess.Kind.ARTIFACT_DESTINATION,
        ArtifactDownloadTransport.destination(uri),
        "download");
  }

  ArtifactDownloadTransport transport(Set<ResourceAccess> grants, int bytes, int redirects) {
    return new ArtifactDownloadTransport(
        ResourceAuthorizer.trusted(grants), Duration.ofSeconds(2), bytes, redirects);
  }

  @Test
  @DisplayName("denies an initial destination before connecting or exposing signed tokens")
  void deniedInitial() {
    var failure =
        assertThrows(
            SecurityException.class, () -> transport(Set.of(), 16, 2).download(first, Map.of()));
    assertThat(sourceConnections.get()).isZero();
    assertThat(failure.toString()).doesNotContain("FAKE_SIGNED_CANARY");
    assertThat(grant(first).toString()).doesNotContain("FAKE_SIGNED_CANARY");
  }

  @Test
  @DisplayName("validates every redirect before connecting to a denied origin")
  void deniedRedirect() {
    redirect = second.toString();
    assertThrows(
        SecurityException.class,
        () ->
            transport(Set.of(grant(first)), 16, 2)
                .download(first, Map.of("Authorization", "FAKE_CREDENTIAL")));
    assertThat(sourceConnections.get()).isEqualTo(1);
    assertThat(targetConnections.get()).isZero();
  }

  @Test
  @DisplayName("permits trusted signed destinations while dropping credentials across origins")
  void allowedRedirect() {
    redirect = second.toString();
    assertThat(
            transport(Set.of(grant(first), grant(second)), 16, 2)
                .download(first, Map.of("Authorization", "FAKE_CREDENTIAL")))
        .asString(StandardCharsets.UTF_8)
        .isEqualTo("artifact");
    assertThat(receivedCredential.get()).isNull();
  }

  @Test
  @DisplayName("forwards credentials across origins only with an explicit separate grant")
  void explicitForwarding() {
    redirect = second.toString();
    var forwarding =
        new ResourceAccess(
            ResourceAccess.Kind.CREDENTIAL_FORWARDING,
            ArtifactDownloadTransport.origin(first)
                + " -> "
                + ArtifactDownloadTransport.origin(second),
            "forward");
    transport(Set.of(grant(first), grant(second), forwarding), 16, 2)
        .download(first, Map.of("Authorization", "FAKE_CREDENTIAL"));
    assertThat(receivedCredential.get()).isEqualTo("FAKE_CREDENTIAL");
  }

  @Test
  @DisplayName("bounds redirect loops and downloaded bytes")
  void bounds() {
    redirect = first.toString();
    assertThrows(
        IllegalStateException.class,
        () -> transport(Set.of(grant(first)), 16, 1).download(first, Map.of()));
    assertThat(sourceConnections.get()).isEqualTo(2);
    redirect = null;
    assertThrows(
        IllegalStateException.class,
        () -> transport(Set.of(grant(first)), 4, 1).download(first, Map.of()));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "file:///tmp/artifact",
        "http://user:FAKE_CREDENTIAL@localhost/artifact",
        "http://localhost/a/../artifact",
        "http://localhost/%2e%2e/artifact"
      })
  @DisplayName("rejects unsupported, credential-bearing and ambiguous destinations")
  void invalid(String uri) {
    assertThrows(
        IllegalArgumentException.class,
        () -> ArtifactDownloadTransport.destination(URI.create(uri)));
  }

  @Test
  @DisplayName("an explicit denial restricts even a trusted standalone resource")
  void intersection() {
    var access = grant(first);
    var trusted = ResourceAuthorizer.trusted(Set.of(access));
    assertThrows(SecurityException.class, () -> trusted.intersect(_ -> false).require(access));
    assertThat(trusted.permits(grant(second))).isFalse();
  }
}

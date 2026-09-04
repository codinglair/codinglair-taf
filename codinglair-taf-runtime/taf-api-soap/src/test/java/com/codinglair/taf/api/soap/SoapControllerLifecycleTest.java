package com.codinglair.taf.api.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.codinglair.taf.runtime.core.controller.ControllerContext;
import com.codinglair.taf.runtime.core.controller.ControllerState;
import com.codinglair.taf.runtime.core.controller.EnvironmentAccess;
import com.codinglair.taf.runtime.core.reporting.ArtifactCollector;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SOAP controller lifecycle")
class SoapControllerLifecycleTest {
  private final List<HttpServer> servers = new ArrayList<>();

  @AfterEach
  void stopServers() {
    servers.forEach(server -> server.stop(0));
  }

  @Nested
  @DisplayName("Initialization and cleanup")
  class Initialization {
    @Test
    @DisplayName("records failed state after partial initialization")
    void partialInitialization() {
      var controller = new DefaultSoapController("invalid", new SoapControllerSettings());
      assertThrows(SoapControllerException.class, () -> controller.initialize(context("invalid")));
      assertThat(controller.state()).isEqualTo(ControllerState.FAILED);
    }

    @Test
    @DisplayName("close is idempotent before and after initialization")
    void repeatedClose() throws Exception {
      var controller = controller(server("<ok/>", Duration.ZERO), 1024, Duration.ofSeconds(1));
      controller.close();
      controller.close();
      assertThat(controller.state()).isEqualTo(ControllerState.CLOSED);
    }
  }

  @Nested
  @DisplayName("Bounds and cancellation")
  class Bounds {
    @Test
    @DisplayName("rejects responses above the configured size")
    void oversizedResponse() throws Exception {
      var controller =
          controller(server("<very-large-response/>", Duration.ZERO), 5, Duration.ofSeconds(1));
      SoapControllerException failure =
          assertThrows(SoapControllerException.class, () -> controller.exchange(request()));
      assertThat(failure.getCause()).hasMessageContaining("exceeds configured maximum");
    }

    @Test
    @DisplayName("honors operation timeout without hanging")
    void timeout() throws Exception {
      var controller =
          controller(server("<ok/>", Duration.ofMillis(300)), 1024, Duration.ofMillis(50));
      assertTimeoutPreemptively(
          Duration.ofSeconds(2),
          () -> assertThrows(SoapControllerException.class, () -> controller.exchange(request())));
    }
  }

  @Test
  @DisplayName("isolates concurrent named controllers and their evidence")
  void concurrentIsolation() throws Exception {
    var first = controller(server("<first/>", Duration.ZERO), 1024, Duration.ofSeconds(1), "first");
    var second =
        controller(server("<second/>", Duration.ZERO), 1024, Duration.ofSeconds(1), "second");
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Callable<SoapResponse>> calls =
          List.of(() -> first.exchange(request()), () -> second.exchange(request()));
      var results = executor.invokeAll(calls);
      assertThat(results.getFirst().get().envelope()).contains("first");
      assertThat(results.getLast().get().envelope()).contains("second");
      assertThat(first.identity().name()).isEqualTo("first");
      assertThat(second.identity().name()).isEqualTo("second");
    }
  }

  private DefaultSoapController controller(URI endpoint, long maximum, Duration timeout) {
    return controller(endpoint, maximum, timeout, "test");
  }

  private DefaultSoapController controller(
      URI endpoint, long maximum, Duration timeout, String name) {
    var settings = new SoapControllerSettings();
    settings.setEndpoint(endpoint);
    settings.setMaxResponseBytes(maximum);
    settings.setTimeout(timeout);
    var controller = new DefaultSoapController(name, settings);
    controller.initialize(context(name));
    return controller;
  }

  private URI server(String body, Duration delay) throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/soap",
        exchange -> {
          try {
            if (!delay.isZero()) Thread.sleep(delay);
            String response =
                "<s:Envelope xmlns:s='"
                    + SoapVersion.SOAP_11.envelopeNamespace()
                    + "'><s:Body>"
                    + body
                    + "</s:Body></s:Envelope>";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/xml");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
          } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
          } finally {
            exchange.close();
          }
        });
    server.start();
    servers.add(server);
    return URI.create("http://localhost:" + server.getAddress().getPort() + "/soap");
  }

  private static SoapRequest request() {
    return SoapRequest.of(
        SoapVersion.SOAP_11,
        null,
        "<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'><s:Body><ping/></s:Body></s:Envelope>");
  }

  private static ControllerContext context(String name) {
    return new ControllerContext(
        "session-" + name,
        EnvironmentAccess.unavailable(),
        new ArtifactCollector(
            TafTest.of("soap", SoapControllerLifecycleTest.class.getName()),
            "session-" + name,
            "test"));
  }
}

package com.codinglair.taf.virtualization.wiremock;

import static org.assertj.core.api.Assertions.*;

import com.codinglair.taf.runtime.core.TestSession;
import com.codinglair.taf.runtime.environment.*;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

@DisplayName("WireMock virtualization lifecycle")
class WireMockVirtualizationTest {
  private WireMockServer server;

  @BeforeEach
  void startServer() {
    server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop();
  }

  @Nested
  @DisplayName("Mapping lifecycle")
  class MappingLifecycle {
    @Test
    @DisplayName("uses identical mapping code for external and container resources")
    void sameCodeForBothModes() throws Exception {
      for (EnvironmentMode mode : EnvironmentMode.values()) {
        try (TestSession session = TestSession.create()) {
          var scope =
              new WireMockVirtualizationFactory(NetworkFaultPolicy.denyAll())
                  .create(session, resource(mode), "local");
          var handle = scope.add(new VirtualMapping("GET", "/health", 200, "ok-" + mode));
          assertThat(send(handle.endpoint())).isEqualTo("ok-" + mode);
          scope.verify("GET", "/health", 1);
        }
      }
    }

    @Test
    @DisplayName("removes mappings and request events when the session closes")
    void cleanup() throws Exception {
      var session = TestSession.create();
      var scope =
          new WireMockVirtualizationFactory(NetworkFaultPolicy.denyAll())
              .create(session, resource(EnvironmentMode.EXTERNAL), "local");
      URI endpoint = scope.add(new VirtualMapping("GET", "/cleanup", 200, "present")).endpoint();
      assertThat(send(endpoint)).isEqualTo("present");
      session.close();
      assertThat(server.getStubMappings()).isEmpty();
      assertThat(server.getAllServeEvents()).isEmpty();
      assertThatCode(session::close).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("isolates parallel sessions by path and cleanup ownership")
    void parallelIsolation() throws Exception {
      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        List<Future<String>> results =
            java.util.stream.IntStream.range(0, 24)
                .mapToObj(
                    index ->
                        executor.submit(
                            () -> {
                              try (TestSession session = TestSession.create()) {
                                var scope =
                                    new WireMockVirtualizationFactory(NetworkFaultPolicy.denyAll())
                                        .create(
                                            session, resource(EnvironmentMode.EXTERNAL), "local");
                                URI endpoint =
                                    scope
                                        .add(
                                            new VirtualMapping(
                                                "GET", "/parallel", 200, "value-" + index))
                                        .endpoint();
                                String response = send(endpoint);
                                scope.verify("GET", "/parallel", 1);
                                return response;
                              }
                            }))
                .toList();
        assertThat(results)
            .extracting(Future::get)
            .containsExactlyInAnyOrderElementsOf(
                java.util.stream.IntStream.range(0, 24).mapToObj(i -> "value-" + i).toList());
      }
      assertThat(server.getStubMappings()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Fault policy and evidence")
  class FaultBehavior {
    @Test
    @DisplayName("classifies latency evidence and permits bounded latency")
    void latencyEvidence() {
      try (TestSession session = TestSession.create()) {
        var scope =
            new WireMockVirtualizationFactory(NetworkFaultPolicy.denyAll())
                .create(session, resource(EnvironmentMode.EXTERNAL), "local");
        var handle =
            scope.add(
                new VirtualMapping(
                    "GET",
                    "/slow",
                    200,
                    Map.of(),
                    "ok",
                    FaultProfile.latency(Duration.ofMillis(10))));
        assertThat(handle.classification()).isEqualTo(FaultClassification.SIMULATED_LATENCY);
        assertThat(session.getArtifactCollector().getArtifacts().getFirst().content())
            .contains("classification=SIMULATED_LATENCY");
      }
    }

    @Test
    @DisplayName("denies network faults without environment authority and records no mapping")
    void deniedNetworkFault() {
      try (TestSession session = TestSession.create()) {
        var scope =
            new WireMockVirtualizationFactory(NetworkFaultPolicy.denyAll())
                .create(session, resource(EnvironmentMode.EXTERNAL), "production");
        assertThatThrownBy(
                () ->
                    scope.add(
                        new VirtualMapping(
                            "GET",
                            "/fault",
                            200,
                            Map.of(),
                            "",
                            FaultProfile.network(FaultProfile.NetworkFault.CONNECTION_RESET))))
            .isInstanceOf(WireMockVirtualizationException.class)
            .hasMessageContaining("not permitted");
        assertThat(scope.activeMappingIds()).isEmpty();
      }
    }

    @Test
    @DisplayName("classifies authorized network failures without leaking response data")
    void authorizedNetworkFault() {
      try (TestSession session = TestSession.create()) {
        var scope =
            new WireMockVirtualizationFactory(environment -> environment.equals("chaos"))
                .create(session, resource(EnvironmentMode.EXTERNAL), "chaos");
        var handle =
            scope.add(
                new VirtualMapping(
                    "GET",
                    "/fault",
                    200,
                    Map.of(),
                    "secret-body",
                    FaultProfile.network(FaultProfile.NetworkFault.EMPTY_RESPONSE)));
        assertThat(handle.classification())
            .isEqualTo(FaultClassification.SIMULATED_NETWORK_FAILURE);
        assertThat(session.getArtifactCollector().getArtifacts().getFirst().content())
            .contains("SIMULATED_NETWORK_FAILURE")
            .doesNotContain("secret-body");
      }
    }
  }

  private EnvironmentResource resource(EnvironmentMode mode) {
    return new EnvironmentResource() {
      public String id() {
        return UUID.randomUUID().toString();
      }

      public EnvironmentType type() {
        return WireMockEnvironmentProvider.TYPE;
      }

      public EnvironmentMode mode() {
        return mode;
      }

      public Map<String, String> properties() {
        return Map.of(WireMockEnvironmentProvider.BASE_URL, server.baseUrl());
      }

      public EnvironmentDiagnostic diagnose() {
        return new EnvironmentDiagnostic(
            EnvironmentStatus.READY, "ready", "", Map.of(), java.time.Instant.now());
      }

      public void cleanup() {}
    };
  }

  private static String send(URI uri) throws Exception {
    return HttpClient.newHttpClient()
        .send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString())
        .body();
  }
}

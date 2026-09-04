package com.codinglair.taf.api.rest;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.controller.ControllerContext;
import com.codinglair.taf.runtime.core.controller.EnvironmentAccess;
import com.codinglair.taf.runtime.core.reporting.ArtifactCollector;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.restassured.http.Method;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.*;

@DisplayName("REST controller WireMock vertical slice")
class RestControllerWireMockTest {
  private WireMockServer server;

  @BeforeEach
  void start() {
    server = new WireMockServer(0);
    server.start();
    configureFor("localhost", server.port());
  }

  @AfterEach
  void stop() {
    server.stop();
  }

  @Nested
  @DisplayName("protocol behavior")
  class ProtocolBehavior {
    @Test
    @DisplayName("executes success and rich response assertions")
    void success() {
      stubFor(
          get(urlPathEqualTo("/items/7"))
              .withQueryParam("view", equalTo("full"))
              .willReturn(
                  okJson("{\"id\":7,\"name\":\"book\"}")
                      .withHeader("X-Trace", "abc")
                      .withHeader("Set-Cookie", "sid=123")));
      var fixture = controller("success");
      var response =
          fixture.controller.execute(
              RestRequest.request(Method.GET, "/items/{id}")
                  .pathParameter("id", 7)
                  .queryParameter("view", "full")
                  .build());
      response
          .assertStatus(200)
          .assertHeader("X-Trace", "abc")
          .assertCookie("sid", "123")
          .assertJsonPath("id", 7)
          .assertJsonSchema("{\"type\":\"object\",\"required\":[\"id\",\"name\"]}");
      assertThat(fixture.artifacts.getArtifacts()).hasSize(2);
    }

    @Test
    @DisplayName("supports negative responses without treating them as transport failures")
    void negative() {
      stubFor(delete("/missing").willReturn(notFound().withBody("absent")));
      controller("negative")
          .controller
          .execute(RestRequest.request(Method.DELETE, "/missing").build())
          .assertStatus(404)
          .assertBody("absent");
    }

    @Test
    @DisplayName("polls until an eventual response is ready")
    void polling() {
      stubFor(
          get("/job")
              .inScenario("job")
              .whenScenarioStateIs(Scenario.STARTED)
              .willReturn(aResponse().withStatus(202))
              .willSetStateTo("ready"));
      stubFor(get("/job").inScenario("job").whenScenarioStateIs("ready").willReturn(ok("done")));
      var response =
          controller("poll")
              .controller
              .await(
                  RestRequest.request(Method.GET, "/job").build(),
                  value -> value.statusCode() == 200,
                  Duration.ofSeconds(2),
                  Duration.ofMillis(10));
      response.assertBody("done");
      verify(2, getRequestedFor(urlEqualTo("/job")));
    }

    @Test
    @DisplayName("uploads multipart and preserves binary request support")
    void multipart() {
      stubFor(
          post("/upload")
              .withMultipartRequestBody(
                  aMultipart()
                      .withName("file")
                      .withHeader("Content-Type", containing("application/octet-stream")))
              .willReturn(created()));
      controller("multipart")
          .controller
          .execute(
              RestRequest.request(Method.POST, "/upload")
                  .multipart(
                      new RestMultipart(
                          "file", "sample.bin", "application/octet-stream", new byte[] {0, 1, 2}))
                  .build())
          .assertStatus(201);
    }
  }

  @Nested
  @DisplayName("isolation and evidence")
  class IsolationAndEvidence {
    @Test
    @DisplayName("redacts sensitive headers and binary bodies from evidence")
    void redaction() {
      stubFor(post("/secure").willReturn(ok("ok")));
      var fixture = controller("redaction");
      fixture.controller.execute(
          RestRequest.request(Method.POST, "/secure")
              .header("Authorization", "Bearer canary-secret")
              .body(new byte[] {0, 1}, "application/octet-stream")
              .build());
      String evidence =
          fixture.artifacts.getArtifacts().stream()
              .map(a -> a.content())
              .reduce("", String::concat);
      assertThat(evidence).doesNotContain("canary-secret").contains("****", "[BINARY 2 bytes]");
    }

    @Test
    @DisplayName("parallel reusable specifications do not leak state")
    void parallel() throws Exception {
      stubFor(get(urlPathEqualTo("/parallel")).willReturn(okJson("{\"ok\":true}")));
      RestRequest request = RestRequest.request(Method.GET, "/parallel").build();
      var first = controller("one");
      var second = controller("two");
      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        List<Callable<Integer>> calls =
            List.of(
                () -> first.controller.execute(request).statusCode(),
                () -> second.controller.execute(request).statusCode());
        assertThat(executor.invokeAll(calls))
            .allSatisfy(future -> assertThat(future.get()).isEqualTo(200));
      }
      assertThat(first.artifacts.getArtifacts()).hasSize(2);
      assertThat(second.artifacts.getArtifacts()).hasSize(2);
    }
  }

  private Fixture controller(String name) {
    RestControllerSettings settings = new RestControllerSettings();
    settings.setBaseUrl(URI.create(server.baseUrl()));
    ArtifactCollector artifacts =
        new ArtifactCollector(TafTest.of(name, getClass().getName()), name, name);
    DefaultRestController controller =
        new DefaultRestController(name, settings, RestContractValidator.NONE);
    controller.initialize(new ControllerContext(name, EnvironmentAccess.unavailable(), artifacts));
    return new Fixture(controller, artifacts);
  }

  private record Fixture(DefaultRestController controller, ArtifactCollector artifacts) {}
}

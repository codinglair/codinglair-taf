package com.codinglair.taf.api.rest;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
      stubFor(
          post(urlPathEqualTo("/secure"))
              .willReturn(
                  okJson("{\"token\":\"response-canary\",\"result\":\"ok\"}")
                      .withHeader("Set-Cookie", "sid=response-cookie-canary")));
      var fixture = controller("redaction");
      fixture.controller.execute(
          RestRequest.request(Method.POST, "/secure")
              .header("Authorization", "Bearer canary-secret")
              .cookie("sid", "cookie-canary")
              .queryParameter("access-token", "query-canary")
              .body("{\"password\":\"body-canary\",\"name\":\"safe\"}", "application/json")
              .build());
      String evidence =
          fixture.artifacts.getArtifacts().stream()
              .map(a -> a.content())
              .reduce("", String::concat);
      assertThat(evidence)
          .doesNotContain(
              "canary-secret",
              "cookie-canary",
              "query-canary",
              "body-canary",
              "response-canary",
              "response-cookie-canary")
          .contains("[REDACTED]", "name", "safe", "result", "ok");
    }

    @Test
    @DisplayName("retains the response when validation fails")
    void assertionFailure() {
      stubFor(get("/assertion").willReturn(okJson("{\"state\":\"wrong\"}")));
      var fixture =
          controller(
              "assertion",
              settings(),
              (request, response) -> {
                throw new AssertionError("contract expected ready");
              });

      assertThatThrownBy(
              () ->
                  fixture.controller.execute(RestRequest.request(Method.GET, "/assertion").build()))
          .isInstanceOf(AssertionError.class);

      assertThat(fixture.artifacts.getArtifacts())
          .extracting(artifact -> artifact.type())
          .containsExactly("http-request", "http-response", "http-failure");
      assertThat(allEvidence(fixture)).contains("response-validation", "wrong");
    }

    @Test
    @DisplayName("retains bounded diagnostics for a refused connection")
    void connectionFailure() {
      RestControllerSettings settings = new RestControllerSettings();
      settings.setBaseUrl(URI.create("http://127.0.0.1:1"));
      settings.setTimeout(Duration.ofMillis(200));
      var fixture = controller("refused", settings, RestContractValidator.NONE);

      assertThatThrownBy(
              () ->
                  fixture.controller.execute(
                      RestRequest.request(Method.GET, "/unavailable").build()))
          .isInstanceOf(java.net.ConnectException.class);

      assertThat(fixture.artifacts.getArtifacts())
          .extracting(artifact -> artifact.type())
          .containsExactly("http-request", "http-failure");
      assertThat(allEvidence(fixture))
          .contains("connection-or-before-response", "operationId=refused-rest-1")
          .doesNotContain("null");
    }

    @Test
    @DisplayName("truncates text after redaction and safely omits binary bodies")
    void bodyPolicy() {
      stubFor(post("/large").willReturn(ok("done")));
      RestControllerSettings settings = new RestControllerSettings();
      settings.setBaseUrl(URI.create(server.baseUrl()));
      settings.setMaxEvidenceBytes(256);
      var textFixture = controller("large", settings, RestContractValidator.NONE);
      textFixture.controller.execute(
          RestRequest.request(Method.POST, "/large")
              .body(
                  "{\"password\":\"large-secret\",\"data\":\"" + "x".repeat(500) + "\"}",
                  "application/json")
              .build());
      assertThat(allEvidence(textFixture))
          .contains("[TRUNCATED after sanitization; originalSize=")
          .doesNotContain("large-secret");

      var binaryFixture = controller("binary");
      binaryFixture.controller.execute(
          RestRequest.request(Method.POST, "/large")
              .body(new byte[] {0, 1, 2}, "application/octet-stream")
              .build());
      assertThat(allEvidence(binaryFixture)).contains("[BINARY OMITTED; originalSize=3]");
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
    return controller(name, settings(), RestContractValidator.NONE);
  }

  private RestControllerSettings settings() {
    RestControllerSettings settings = new RestControllerSettings();
    settings.setBaseUrl(URI.create(server.baseUrl()));
    return settings;
  }

  private Fixture controller(
      String name, RestControllerSettings settings, RestContractValidator validator) {
    ArtifactCollector artifacts =
        new ArtifactCollector(TafTest.of(name, getClass().getName()), name, name);
    DefaultRestController controller = new DefaultRestController(name, settings, validator);
    controller.initialize(new ControllerContext(name, EnvironmentAccess.unavailable(), artifacts));
    return new Fixture(controller, artifacts);
  }

  private static String allEvidence(Fixture fixture) {
    return fixture.artifacts.getArtifacts().stream()
        .map(artifact -> artifact.content())
        .reduce("", String::concat);
  }

  private record Fixture(DefaultRestController controller, ArtifactCollector artifacts) {}
}

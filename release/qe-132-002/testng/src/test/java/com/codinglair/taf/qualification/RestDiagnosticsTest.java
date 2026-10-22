package com.codinglair.taf.qualification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.api.rest.RestAutoConfiguration;
import com.codinglair.taf.api.rest.RestController;
import com.codinglair.taf.api.rest.RestContractValidator;
import com.codinglair.taf.api.rest.RestRequest;
import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.lifecycle.LifecycleViolationException;
import com.codinglair.taf.runtime.core.reporting.impl.allure.AllureReporterAutoConfiguration;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.http.Method;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** Released-candidate REST diagnostics exercised only through public APIs and an active session. */
@ContextConfiguration(classes = {TafRuntimeAutoConfiguration.class, AllureReporterAutoConfiguration.class})
public final class RestDiagnosticsTest extends com.codinglair.taf.runtime.testng.TafBaseTest {
  private static final String SECRET = "qe-132-002-secret-sentinel";
  private HttpServer server;
  private java.util.concurrent.ExecutorService serverExecutor;

  @BeforeClass
  public void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    serverExecutor = Executors.newVirtualThreadPerTaskExecutor();
    server.setExecutor(serverExecutor);
    server.createContext("/ok", exchange -> respond(exchange, 200, "{\"result\":\"ok\"}"));
    server.createContext("/client-error", exchange -> respond(exchange, 422, "invalid request"));
    server.createContext("/server-error", exchange -> respond(exchange, 503, "temporarily unavailable"));
    server.createContext("/large", exchange -> respond(exchange, 200, "x".repeat(2048)));
    server.createContext("/slow", exchange -> {
      try { Thread.sleep(500); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
      respond(exchange, 200, "late");
    });
    server.start();
  }

  @AfterClass(alwaysRun = true)
  public void stopServer() {
    if (server != null) server.stop(0);
    if (serverExecutor != null) serverExecutor.close();
  }

  @Test(description = "Captures sanitized REST success, HTTP error, timing, and bounded-body diagnostics")
  public void capturesSuccessAndHttpErrorsWithSanitizedDetails() {
    withController(300, 256, controller -> {
      controller.execute(RestRequest.request(Method.GET, "/ok").queryParameter("token", SECRET)
          .header("Authorization", "Bearer " + SECRET).build()).assertStatus(200);
      controller.execute(RestRequest.request(Method.GET, "/client-error").build()).assertStatus(422);
      controller.execute(RestRequest.request(Method.GET, "/server-error").build()).assertStatus(503);
      controller.execute(RestRequest.request(Method.GET, "/large").build()).assertStatus(200);
    });
    String evidence = allEvidence();
    assertThat(evidence).contains("method=GET", "status=200", "status=422", "status=503", "durationMs=", "[TRUNCATED after sanitization")
        .doesNotContain(SECRET);
  }

  @Test(description = "Retains response evidence for assertion failures and classifies read timeouts")
  public void retainsAssertionAfterResponseAndTransportFailures() {
    withController(100, 1024, (request, response) -> {
      if (request.path().equals("/ok")) throw new AssertionError("expected status 201 after response");
    }, controller -> {
      assertThatThrownBy(() -> controller.execute(RestRequest.request(Method.GET, "/ok").build()))
          .isInstanceOf(AssertionError.class);
      assertThatThrownBy(() -> controller.execute(RestRequest.request(Method.GET, "/slow").build()))
          .hasRootCauseInstanceOf(java.net.SocketTimeoutException.class);
    });
    assertThat(allEvidence()).contains("status=200", "response-validation", "connection-or-before-response");
  }

  @Test(description = "Rejects a REST controller call made without the owning invocation session")
  public void rejectsCallsWithoutTheInvocationSession() {
    withController(300, 1024, controller -> {
      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        assertThatThrownBy(() -> executor.submit(() -> controller.execute(RestRequest.request(Method.GET, "/ok").build())).get())
            .hasRootCauseInstanceOf(LifecycleViolationException.class)
            .rootCause().hasMessageContaining("TAF-LIFECYCLE-001");
      }
    });
  }

  @Test(description = "Preserves an intentional post-response failure as a failed report entry")
  public void deliberatelyFailsAfterResponseSoReportPreservesFailure() {
    withController(300, 1024, controller -> controller.execute(RestRequest.request(Method.GET, "/ok").build()).assertStatus(200));
    throw new AssertionError("QE-132-002 expected diagnostic failure marker");
  }

  private void withController(int timeoutMillis, int maxEvidenceBytes, java.util.function.Consumer<RestController> action) {
    withController(timeoutMillis, maxEvidenceBytes, RestContractValidator.NONE, action);
  }

  private void withController(int timeoutMillis, int maxEvidenceBytes, RestContractValidator validator,
      java.util.function.Consumer<RestController> action) {
    new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(TafRuntimeAutoConfiguration.class, RestAutoConfiguration.class))
        .withBean(RestContractValidator.class, () -> validator)
        .withPropertyValues("taf.api.rest.enabled=true", "taf.api.rest.base-url=http://127.0.0.1:" + server.getAddress().getPort(),
            "taf.api.rest.timeout=" + timeoutMillis + "ms", "taf.api.rest.max-evidence-bytes=" + maxEvidenceBytes)
        .run(context -> {
          testSession().getControllerRegistry().register(RestController.class, "qe", context.getBean(com.codinglair.taf.api.rest.RestControllerFactory.class).create("default"));
          action.accept(testSession().getController(RestController.class, "qe"));
        });
  }

  private String allEvidence() {
    return testSession().getArtifactCollector().getArtifacts().stream().map(artifact -> artifact.content()).reduce("", String::concat);
  }

  private static void respond(HttpExchange exchange, int status, String value) throws IOException {
    byte[] body = value.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }
}

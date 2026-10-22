package com.codinglair.taf.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.api.soap.*;
import com.codinglair.taf.contracts.*;
import com.codinglair.taf.database.*;
import com.codinglair.taf.observability.*;
import com.codinglair.taf.runtime.core.reporting.RedactionPipeline;
import com.codinglair.taf.runtime.environment.*;
import com.codinglair.taf.runtime.file.*;
import com.codinglair.taf.virtualization.wiremock.*;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testng.annotations.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;

/** Real deterministic local targets exercised only through published TAF APIs. */
public final class DeterministicCapabilitiesConsumerTest
    extends com.codinglair.taf.runtime.testng.TafBaseTest {

  @Test
  public void exchangesSoapWithLocalService() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    String response = envelope("<m:result xmlns:m='urn:test'>ok</m:result>");
    server.createContext("/soap", exchange -> {
      exchange.getRequestBody().readAllBytes();
      byte[] body = response.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "text/xml");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(TafRuntimeAutoConfiguration.class, SoapAutoConfiguration.class))
          .withPropertyValues("taf.api.soap.enabled=true",
              "taf.api.soap.controllers.local.endpoint=http://127.0.0.1:" + server.getAddress().getPort() + "/soap")
          .run(context -> {
            testSession().getControllerRegistry().register(SoapController.class, "local",
                context.getBean(SoapControllerFactory.class).create("local"));
            SoapResponse actual = testSession().getController(SoapController.class, "local")
                .exchange(SoapRequest.of(SoapVersion.SOAP_11, "urn:test", envelope("<m:ping xmlns:m='urn:test'/>")));
            assertThat(SoapXml.xpath(actual.envelope(), "string(//m:result)", Map.of("m", "urn:test"))).isEqualTo("ok");
          });
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void comparesFilesInsideLocalSandbox() throws Exception {
    Path root = Files.createTempDirectory("ver-132-files-");
    try {
      Files.writeString(root.resolve("expected.json"), "{\"id\":1,\"value\":\"ok\"}");
      Files.writeString(root.resolve("actual.json"), "{\"value\":\"ok\",\"id\":1}");
      testSession().getControllerRegistry().register(FileController.class, "local", new DefaultFileController("local", root, 1024 * 1024));
      var result = testSession().getController(FileController.class, "local")
          .compare(new FileComparisonRequest(Path.of("expected.json"), Path.of("actual.json"), FileFormat.JSON, FileComparisonOptions.defaults()));
      assertThat(result.matched()).isTrue();
    } finally {
      try (var paths = Files.walk(root)) {
        paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (Exception failure) { throw new IllegalStateException(failure); } });
      }
    }
  }

  @Test
  public void queriesRealPostgresqlTarget() {
    try (var postgres = new PostgreSQLContainer("postgres:17-alpine")) {
      postgres.start();
      var descriptor = new SutConnectionDescriptor("local", "postgresql", postgres.getJdbcUrl(),
          postgres.getUsername(), "", ConnectionMode.TESTCONTAINERS, DatabaseAccess.READ_WRITE,
          Duration.ofSeconds(5), CleanupPolicy.NONE, true, false, Set.of(), "");
      var controller = new DefaultDatabaseController(descriptor,
          (ignored, sessionId) -> DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
      testSession().getControllerRegistry().register(DatabaseController.class, "local", controller);
      assertThat(testSession().getController(DatabaseController.class, "local")
          .query("select 'database-ok' as result").rows().getFirst()).containsEntry("result", "database-ok");
    }
  }

  @Test
  public void validatesRealOpenApiDocument() {
    String openApi = "openapi: 3.1.0\ninfo:\n  title: Local\n  version: '1'\npaths:\n  /health:\n    get:\n      responses:\n        '200':\n          description: ok\n";
    var service = new ContractService(List.of(new OpenApiContractAdapter(), new AsyncApiContractAdapter()));
    assertThat(service.validate(new ContractDocument(ContractFormat.OPENAPI, "3.1", "1", openApi)).valid()).isTrue();
  }

  @Test
  public void virtualizesLocalHttpAndCleansOwnedMapping() throws Exception {
    WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    server.start();
    WireMockVirtualization scope = null;
    try {
      EnvironmentResource resource = externalWireMock(server);
      scope = new WireMockVirtualizationFactory(NetworkFaultPolicy.denyAll()).create(testSession(), resource, "local");
      URI endpoint = scope.add(new VirtualMapping("GET", "/qualification", 200, "virtualized-ok")).endpoint();
      String body = HttpClient.newHttpClient().send(HttpRequest.newBuilder(endpoint).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
      assertThat(body).isEqualTo("virtualized-ok");
      scope.verify("GET", "/qualification", 1);
    } finally {
      if (scope != null) scope.close();
      server.stop();
    }
  }

  @Test
  public void queriesDeterministicLocalTelemetryProvider() {
    Instant now = Instant.now();
    PrometheusMetricProvider provider = new PrometheusMetricProvider() {
      public String name() { return "local-metric-target"; }
      public TelemetryQueryResult query(TelemetryQuery query) {
        return new TelemetryQueryResult(TelemetryType.METRIC,
            List.of(new TelemetryRecord(now, Map.of("service", "orders"), "requests_total 7")), 1, false, "local");
      }
    };
    var controller = new DefaultObservabilityController("local", List.of(provider), new RedactionPipeline(), ObservabilityLimits.DEFAULTS);
    testSession().getControllerRegistry().register(ObservabilityController.class, "local", controller);
    var result = testSession().getController(ObservabilityController.class, "local")
        .query(TelemetryQuery.metrics("requests_total", now.minusSeconds(1), now.plusSeconds(1), 10));
    assertThat(result.records()).singleElement().satisfies(record -> assertThat(record.payload()).contains("7"));
  }

  private static String envelope(String body) {
    return "<s:Envelope xmlns:s='" + SoapVersion.SOAP_11.envelopeNamespace() + "'><s:Body>" + body + "</s:Body></s:Envelope>";
  }

  private static EnvironmentResource externalWireMock(WireMockServer server) {
    return new EnvironmentResource() {
      public String id() { return "local-wiremock"; }
      public EnvironmentType type() { return WireMockEnvironmentProvider.TYPE; }
      public EnvironmentMode mode() { return EnvironmentMode.EXTERNAL; }
      public Map<String, String> properties() { return Map.of(WireMockEnvironmentProvider.BASE_URL, server.baseUrl()); }
      public EnvironmentDiagnostic diagnose() { return new EnvironmentDiagnostic(EnvironmentStatus.READY, "ready", "", Map.of(), Instant.now()); }
      public void cleanup() {}
    };
  }
}

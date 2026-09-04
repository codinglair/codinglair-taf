package com.codinglair.taf.api.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.controller.ControllerContext;
import com.codinglair.taf.runtime.core.controller.EnvironmentAccess;
import com.codinglair.taf.runtime.core.reporting.ArtifactCollector;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import com.codinglair.taf.runtime.secret.ResolvedSecret;
import com.codinglair.taf.runtime.secret.SecretManager;
import com.codinglair.taf.runtime.secret.SecretRequestContext;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SOAP controller protocol integration")
class SoapControllerIntegrationTest {
  private HttpServer server;

  @AfterEach
  void stop() {
    if (server != null) server.stop(0);
  }

  @Nested
  @DisplayName("SOAP versions and faults")
  class Versions {
    @Test
    @DisplayName("exchanges SOAP 1.1 and captures neutral evidence")
    void soap11() throws Exception {
      String response = envelope(SoapVersion.SOAP_11, "<m:result xmlns:m='urn:test'>ok</m:result>");
      Exchange exchange = exchange(response, "text/xml");
      SoapResponse actual =
          exchange.controller.exchange(
              SoapRequest.of(
                  SoapVersion.SOAP_11,
                  "urn:test",
                  envelope(SoapVersion.SOAP_11, "<m:ping xmlns:m='urn:test'/>")));
      assertThat(actual.statusCode()).isEqualTo(200);
      assertThat(SoapXml.xpath(actual.envelope(), "string(//m:result)", Map.of("m", "urn:test")))
          .isEqualTo("ok");
      assertThat(exchange.artifacts.getArtifacts()).hasSize(2);
    }

    @Test
    @DisplayName("parses SOAP 1.2 faults")
    void soap12Fault() throws Exception {
      String fault =
          envelope(
              SoapVersion.SOAP_12,
              "<s:Fault xmlns:s='http://www.w3.org/2003/05/soap-envelope'><s:Code><s:Value>s:Sender</s:Value></s:Code><s:Reason><s:Text>bad request</s:Text></s:Reason><s:Detail>invalid</s:Detail></s:Fault>");
      Exchange exchange = exchange(fault, "application/soap+xml");
      SoapResponse actual =
          exchange.controller.exchange(
              SoapRequest.of(
                  SoapVersion.SOAP_12,
                  null,
                  envelope(SoapVersion.SOAP_12, "<m:ping xmlns:m='urn:test'/>")));
      assertThat(actual.soapFault())
          .hasValueSatisfying(
              value -> {
                assertThat(value.code()).isEqualTo("s:Sender");
                assertThat(value.reason()).isEqualTo("bad request");
              });
    }
  }

  @Test
  @DisplayName("sends and receives multipart attachments without storing binary evidence")
  void attachments() throws Exception {
    String boundary = "reply";
    String response =
        "--reply\r\nContent-Type: text/xml\r\nContent-ID: <root>\r\n\r\n"
            + envelope(SoapVersion.SOAP_11, "<ok/>")
            + "\r\n--reply\r\nContent-Type: application/octet-stream\r\nContent-ID: <doc>\r\n\r\nDATA\r\n--reply--\r\n";
    Exchange exchange = exchange(response, "multipart/related; boundary=\"" + boundary + "\"");
    SoapRequest request =
        new SoapRequest(
            SoapVersion.SOAP_11,
            null,
            envelope(SoapVersion.SOAP_11, "<upload/>"),
            Map.of(),
            List.of(
                new SoapAttachment(
                    "input", "text/plain", "INPUT".getBytes(StandardCharsets.UTF_8))),
            SoapMessageSecurity.NONE);
    SoapResponse actual = exchange.controller.exchange(request);
    assertThat(actual.attachments())
        .singleElement()
        .satisfies(
            part ->
                assertThat(new String(part.content(), StandardCharsets.UTF_8)).isEqualTo("DATA"));
    assertThat(exchange.artifacts.getArtifacts())
        .allSatisfy(artifact -> assertThat(artifact.content()).doesNotContain("DATA", "INPUT"));
  }

  @Test
  @DisplayName("removes WS-Security values before collecting request evidence")
  void securityEvidenceIsSanitized() throws Exception {
    Exchange exchange = exchange(envelope(SoapVersion.SOAP_11, "<ok/>"), "text/xml");
    SecretManager manager =
        new SecretManager() {
          public ResolvedSecret resolve(String reference, SecretRequestContext context) {
            return ResolvedSecret.of(
                (reference.endsWith("USER") ? "private-user-canary" : "private-password-canary")
                    .toCharArray());
          }

          public void verifyReady(String reference) {}
        };
    var security =
        new UsernameTokenSecurity(
            manager,
            "secret://env/USER",
            "secret://env/PASSWORD",
            new SecretRequestContext("soap", "session", "test", true));
    exchange.controller.exchange(
        new SoapRequest(
            SoapVersion.SOAP_11,
            null,
            envelope(SoapVersion.SOAP_11, "<ping/>"),
            Map.of(),
            List.of(),
            security));
    assertThat(exchange.artifacts.getArtifacts())
        .allSatisfy(
            artifact ->
                assertThat(artifact.content())
                    .doesNotContain("private-user-canary", "private-password-canary"));
  }

  private Exchange exchange(String response, String contentType) throws Exception {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/soap",
        request -> {
          request.getRequestBody().readAllBytes();
          byte[] bytes = response.getBytes(StandardCharsets.ISO_8859_1);
          request.getResponseHeaders().add("Content-Type", contentType);
          request.sendResponseHeaders(200, bytes.length);
          request.getResponseBody().write(bytes);
          request.close();
        });
    server.start();
    SoapControllerSettings settings = new SoapControllerSettings();
    settings.setEndpoint(URI.create("http://localhost:" + server.getAddress().getPort() + "/soap"));
    var controller = new DefaultSoapController("test", settings);
    var artifacts =
        new ArtifactCollector(TafTest.of("soap", getClass().getName()), "session", "test");
    controller.initialize(
        new ControllerContext("session", EnvironmentAccess.unavailable(), artifacts));
    return new Exchange(controller, artifacts);
  }

  private static String envelope(SoapVersion version, String body) {
    return "<s:Envelope xmlns:s='"
        + version.envelopeNamespace()
        + "'><s:Body>"
        + body
        + "</s:Body></s:Envelope>";
  }

  private record Exchange(SoapController controller, ArtifactCollector artifacts) {}
}

package com.codinglair.taf.mcp.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@SpringBootTest(
    classes = TafMcpHttpApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "taf.mcp.http.issuer-uri=https://issuer.invalid",
      "taf.mcp.http.audience=taf-mcp",
      "taf.mcp.kind-reference.enabled=true",
      "spring.main.banner-mode=off"
    })
@Import(HttpExternalClientSmokeTest.Fixture.class)
@DisplayName("External Streamable HTTP MCP client compatibility")
class HttpExternalClientSmokeTest {
  @LocalServerPort int port;

  @Test
  @DisplayName("frames initialize response bytes consistently with HTTP headers")
  void framesInitializeResponse() throws Exception {
    String body =
        """
        {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"framing-client","version":"1.0"}}}
        """
            .strip();
    byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
    String request =
        "POST /mcp HTTP/1.1\r\n"
            + "Host: localhost:"
            + port
            + "\r\nAuthorization: Bearer gate-token\r\n"
            + "Content-Type: application/json\r\n"
            + "Accept: application/json, text/event-stream\r\n"
            + "Content-Length: "
            + bodyBytes.length
            + "\r\n\r\n"
            + body;

    byte[] raw;
    try (var socket = new Socket("localhost", port)) {
      socket.setSoTimeout(1_000);
      socket.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
      var response = new java.io.ByteArrayOutputStream();
      try {
        socket.getInputStream().transferTo(response);
      } catch (SocketTimeoutException _) {
        // A persistent HTTP/1.1 connection remains open after the complete response.
      }
      raw = response.toByteArray();
    }

    String response = new String(raw, StandardCharsets.ISO_8859_1);
    int boundary = response.indexOf("\r\n\r\n");
    assertThat(boundary).isPositive();
    String headers = response.substring(0, boundary);
    byte[] responseBody = java.util.Arrays.copyOfRange(raw, boundary + 4, raw.length);
    assertThat(headers).contains("HTTP/1.1 200");
    assertThat(responseBody).as("raw response body after framing headers").isNotEmpty();
    if (headers.toLowerCase().contains("transfer-encoding: chunked")) {
      int lineEnd = indexOf(responseBody, "\r\n".getBytes(StandardCharsets.US_ASCII));
      assertThat(lineEnd).as("chunk-size line terminator").isPositive();
      String chunkSize = new String(responseBody, 0, lineEnd, StandardCharsets.US_ASCII);
      assertThatCode(() -> Integer.parseInt(chunkSize, 16)).doesNotThrowAnyException();
    } else {
      assertThat(headers).containsIgnoringCase("Content-Length:");
    }
  }

  private static int indexOf(byte[] value, byte[] target) {
    outer:
    for (int index = 0; index <= value.length - target.length; index++) {
      for (int offset = 0; offset < target.length; offset++) {
        if (value[index + offset] != target[offset]) {
          continue outer;
        }
      }
      return index;
    }
    return -1;
  }

  @Test
  @DisplayName("connects and discovers governed prompts through authenticated HTTP")
  void discoversPromptsOverAuthenticatedHttp() {
    assertTimeoutPreemptively(
        Duration.ofSeconds(20),
        () -> {
          try (var client = HttpClient.newHttpClient()) {
            var initialized =
                post(
                    client,
                    null,
                    """
                    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"gate-http-client","version":"1.0"}}}
                    """);
            assertThat(initialized.statusCode()).isEqualTo(200);
            assertThat(initialized.body()).contains("codinglair-taf", "prompts");
            String session = initialized.headers().firstValue("Mcp-Session-Id").orElseThrow();

            var prompts =
                post(
                    client,
                    session,
                    """
                    {"jsonrpc":"2.0","id":2,"method":"prompts/list","params":{}}
                    """);
            assertThat(prompts.statusCode()).isEqualTo(200);
            assertThat(prompts.body())
                .contains(
                    "taf.qa.failure-analysis",
                    "taf.qa.execution-summary",
                    "taf.qa.environment-triage",
                    "reportReference")
                .doesNotContain("\"name\":\"arguments\"")
                .doesNotContain("http-secret-canary");

            var prompt =
                post(
                    client,
                    session,
                    """
                    {"jsonrpc":"2.0","id":3,"method":"prompts/get","params":{"name":"taf.qa.execution-summary","arguments":{"reportReference":"taf://report/job-1"}}}
                    """);
            assertThat(prompt.statusCode()).isEqualTo(200);
            assertThat(prompt.body())
                .contains("taf://report/job-1")
                .doesNotContain("http-secret-canary");
          }
        });
  }

  private HttpResponse<String> post(HttpClient client, String session, String body)
      throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
            .timeout(Duration.ofSeconds(10))
            .header("Authorization", "Bearer gate-token")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream");
    if (session != null) {
      builder.header("Mcp-Session-Id", session);
    }
    return client.send(
        builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Fixture {
    @Bean
    JwtDecoder jwtDecoder() {
      return _ ->
          new Jwt(
              "gate-token",
              Instant.now().minusSeconds(1),
              Instant.now().plusSeconds(300),
              Map.of("alg", "none"),
              Map.of(
                  "preferred_username", "http-user",
                  "aud", List.of("taf-mcp"),
                  "taf_project", "kind-smoke",
                  "taf_environment", "kind",
                  "scope", "taf.prompts.read taf.resources.read taf.tools.diagnose"));
    }
  }
}

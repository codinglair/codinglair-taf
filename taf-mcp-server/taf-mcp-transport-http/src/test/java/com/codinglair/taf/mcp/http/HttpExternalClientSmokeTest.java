package com.codinglair.taf.mcp.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

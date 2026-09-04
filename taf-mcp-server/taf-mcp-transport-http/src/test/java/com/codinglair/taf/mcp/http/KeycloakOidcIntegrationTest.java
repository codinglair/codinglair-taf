package com.codinglair.taf.mcp.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "taf.mcp.security.it", matches = "true")
@SpringBootTest(
    classes = TafMcpHttpApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "taf.mcp.http.audience=taf-mcp",
      "taf.mcp.kind-reference.enabled=true",
      "spring.main.banner-mode=off"
    })
@Import(KeycloakOidcIntegrationTest.AuthenticationCapture.class)
@DisplayName("Real Keycloak OIDC resource-server compatibility")
class KeycloakOidcIntegrationTest {
  private static final String PASSWORD = "keycloak-integration-only";
  private static final int KEYCLOAK_PORT = 8080;

  @Container
  static final GenericContainer<?> KEYCLOAK =
      new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:26.3.2"))
          .withCopyToContainer(
              Transferable.of(renderedKindRealm()), "/opt/keycloak/data/import/taf-realm.json")
          .withCommand(
              "start-dev", "--import-realm", "--http-enabled=true", "--hostname-strict=false")
          .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "test-admin")
          .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "integration-test-only")
          .withExposedPorts(KEYCLOAK_PORT)
          .waitingFor(Wait.forHttp("/realms/taf/.well-known/openid-configuration"))
          .withStartupTimeout(Duration.ofMinutes(2));

  @DynamicPropertySource
  static void oidcProperties(DynamicPropertyRegistry registry) {
    registry.add("taf.mcp.http.issuer-uri", KeycloakOidcIntegrationTest::issuer);
  }

  @LocalServerPort int port;

  @Test
  @DisplayName("decodes a Keycloak password-grant token through the complete MCP HTTP path")
  void executesMcpWithRealKeycloakToken() {
    assertTimeoutPreemptively(
        Duration.ofSeconds(30),
        () -> {
          try (var client = HttpClient.newHttpClient()) {
            String accessToken = obtainAccessToken(client);
            var initialized = post(client, accessToken, null, initializeRequest());
            AuthenticationShape shape = AuthenticationCapture.latest().get();
            assertThat(shape).isNotNull();
            System.out.println("Keycloak authentication shape: " + shape.safeDescription());
            assertThat(initialized.statusCode()).isEqualTo(200);
            String session = initialized.headers().firstValue("Mcp-Session-Id").orElseThrow();

            var prompts = post(client, accessToken, session, promptsListRequest());
            assertThat(prompts.statusCode()).isEqualTo(200);
            assertThat(prompts.body()).contains("taf.qa.execution-summary");

            var prompt = post(client, accessToken, session, promptGetRequest());
            assertThat(prompt.statusCode()).isEqualTo(200);
            assertThat(prompt.body()).contains("taf://report/keycloak-integration");

            assertThat(shape.authenticationClass())
                .isEqualTo(JwtAuthenticationToken.class.getName());
            assertThat(shape.authenticated()).isTrue();
            assertThat(shape.principalClass())
                .endsWith("JwtAuthenticationConverter$JwtAuthenticatedPrincipal");
            assertThat(shape.claimNames()).contains("sub", "iss", "aud", "scope");
            assertThat(shape.subPresent()).isTrue();
            assertThat(shape.subBlank()).isFalse();
            assertThat(shape.authenticationNamePresent()).isTrue();
            assertThat(shape.authenticationNameBlank()).isFalse();
            assertThat(shape.resolvedIdentitySource()).isEqualTo("authentication-name");
          }
        });
  }

  private HttpResponse<String> post(HttpClient client, String token, String session, String body)
      throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
            .timeout(Duration.ofSeconds(10))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream");
    if (session != null) builder.header("Mcp-Session-Id", session);
    return client.send(
        builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static String obtainAccessToken(HttpClient client) throws Exception {
    String form =
        "grant_type=password&client_id=taf-kind-smoke&username=taf-kind-smoke&password="
            + URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8);
    var request =
        HttpRequest.newBuilder(URI.create(issuer() + "/protocol/openid-connect/token"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();
    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode body = JsonMapper.builder().build().readTree(response.body());
    return body.required("access_token").asText();
  }

  private static String issuer() {
    return "http://"
        + KEYCLOAK.getHost()
        + ":"
        + KEYCLOAK.getMappedPort(KEYCLOAK_PORT)
        + "/realms/taf";
  }

  private static byte[] renderedKindRealm() {
    try {
      Path current = Path.of("").toAbsolutePath();
      while (current != null && !Files.exists(current.resolve("deploy/kind/realm-template.json"))) {
        current = current.getParent();
      }
      if (current == null)
        throw new IllegalStateException("deploy/kind/realm-template.json not found");
      return Files.readString(current.resolve("deploy/kind/realm-template.json"))
          .replace("@SMOKE_PASSWORD@", PASSWORD)
          .getBytes(StandardCharsets.UTF_8);
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot render the Kind Keycloak realm fixture", exception);
    }
  }

  private static String initializeRequest() {
    return """
        {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"keycloak-integration","version":"1.0"}}}
        """;
  }

  private static String promptsListRequest() {
    return """
        {"jsonrpc":"2.0","id":2,"method":"prompts/list","params":{}}
        """;
  }

  private static String promptGetRequest() {
    return """
        {"jsonrpc":"2.0","id":3,"method":"prompts/get","params":{"name":"taf.qa.execution-summary","arguments":{"reportReference":"taf://report/keycloak-integration"}}}
        """;
  }

  record AuthenticationShape(
      String authenticationClass,
      boolean authenticated,
      boolean authenticationNamePresent,
      boolean authenticationNameBlank,
      String principalClass,
      Set<String> claimNames,
      boolean subPresent,
      boolean subBlank,
      boolean preferredUsernamePresent,
      boolean preferredUsernameBlank,
      String resolvedIdentitySource) {
    static AuthenticationShape from(JwtAuthenticationToken authentication) {
      Jwt jwt = authentication.getToken();
      String name = authentication.getName();
      String sub = jwt.getSubject();
      String username = jwt.getClaimAsString("preferred_username");
      String source =
          name != null && !name.isBlank()
              ? "authentication-name"
              : sub != null && !sub.isBlank()
                  ? "sub"
                  : username != null && !username.isBlank() ? "preferred-username" : "none";
      return new AuthenticationShape(
          authentication.getClass().getName(),
          authentication.isAuthenticated(),
          name != null,
          name == null || name.isBlank(),
          authentication.getPrincipal().getClass().getName(),
          Set.copyOf(jwt.getClaims().keySet()),
          sub != null,
          sub == null || sub.isBlank(),
          username != null,
          username == null || username.isBlank(),
          source);
    }

    String safeDescription() {
      return "authentication.class="
          + authenticationClass
          + ", authentication.authenticated="
          + authenticated
          + ", authentication.name.present="
          + authenticationNamePresent
          + ", authentication.name.blank="
          + authenticationNameBlank
          + ", principal.class="
          + principalClass
          + ", jwt.claim.names="
          + claimNames.stream().sorted().toList()
          + ", jwt.sub.present="
          + subPresent
          + ", jwt.sub.blank="
          + subBlank
          + ", jwt.preferred_username.present="
          + preferredUsernamePresent
          + ", jwt.preferred_username.blank="
          + preferredUsernameBlank
          + ", resolvedUserId.source="
          + resolvedIdentitySource;
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class AuthenticationCapture {
    private static final AtomicReference<AuthenticationShape> LATEST = new AtomicReference<>();

    static AtomicReference<AuthenticationShape> latest() {
      return LATEST;
    }

    @Bean
    ApplicationListener<AuthenticationSuccessEvent> authenticationShapeListener() {
      return event -> {
        if (event.getAuthentication() instanceof JwtAuthenticationToken jwt) {
          LATEST.set(AuthenticationShape.from(jwt));
        }
      };
    }
  }
}

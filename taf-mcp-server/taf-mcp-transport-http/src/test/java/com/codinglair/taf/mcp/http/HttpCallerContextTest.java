package com.codinglair.taf.mcp.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class HttpCallerContextTest {
  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void derivesUserIdFromVerifiedUsernameWhenAuthenticationNameIsEmpty() {
    Jwt jwt =
        new Jwt(
            "token",
            Instant.now().minusSeconds(1),
            Instant.now().plusSeconds(60),
            Map.of("alg", "none"),
            Map.of("preferred_username", "kind-user"));
    SecurityContextHolder.getContext()
        .setAuthentication(
            new JwtAuthenticationToken(
                jwt, java.util.List.of(new SimpleGrantedAuthority("SCOPE_taf.prompts.read")), ""));

    var identity = new HttpCallerContext(new TafMcpHttpProperties()).identity();

    assertThat(identity.userId()).isEqualTo("kind-user");
    assertThat(identity.agentId()).isEqualTo("human:kind-user");
  }

  @Test
  void prefersNonBlankAuthenticatedName() {
    authenticate(Map.of("sub", "subject", "preferred_username", "username"), "principal");

    assertThat(new HttpCallerContext(new TafMcpHttpProperties()).identity().userId())
        .isEqualTo("principal");
  }

  @Test
  void fallsBackToVerifiedSubject() {
    authenticate(Map.of("sub", "subject", "preferred_username", "username"), "");

    assertThat(new HttpCallerContext(new TafMcpHttpProperties()).identity().userId())
        .isEqualTo("subject");
  }

  @Test
  void failsClosedWithoutVerifiedIdentity() {
    authenticate(Map.of("preferred_username", " "), "");

    assertThatThrownBy(() -> new HttpCallerContext(new TafMcpHttpProperties()).identity())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("userId is required");
  }

  @Test
  void transportSnapshotSurvivesClearedSecurityContext() {
    authenticate(
        Map.of(
            "preferred_username", "transport-user",
            "taf_project", "kind-smoke",
            "taf_environment", "kind"),
        "");
    var caller = new HttpCallerContext(new TafMcpHttpProperties());
    var transportContext = caller.captureTransportContext();
    SecurityContextHolder.clearContext();

    assertThat(caller.identity(transportContext).userId()).isEqualTo("transport-user");
    assertThat(caller.project(transportContext)).isEqualTo("kind-smoke");
    assertThat(caller.environment(transportContext)).isEqualTo("kind");
  }

  private static void authenticate(Map<String, Object> claims, String name) {
    Jwt jwt =
        new Jwt(
            "token",
            Instant.now().minusSeconds(1),
            Instant.now().plusSeconds(60),
            Map.of("alg", "none"),
            claims);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new JwtAuthenticationToken(
                jwt,
                java.util.List.of(new SimpleGrantedAuthority("SCOPE_taf.prompts.read")),
                name));
  }
}

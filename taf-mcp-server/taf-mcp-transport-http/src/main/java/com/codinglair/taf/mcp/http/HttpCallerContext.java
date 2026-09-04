package com.codinglair.taf.mcp.http;

import com.codinglair.taf.mcp.security.CallerIdentity;
import com.codinglair.taf.mcp.security.IdentityKind;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Derives transport-neutral identity and scope from the authenticated JWT only. */
final class HttpCallerContext {
  private static final String CALLER_CONTEXT_KEY = HttpCallerContext.class.getName() + ".caller";
  private final TafMcpHttpProperties properties;

  HttpCallerContext(TafMcpHttpProperties properties) {
    this.properties = properties;
  }

  CallerIdentity identity() {
    return identity(authentication());
  }

  CallerIdentity identity(McpTransportContext transportContext) {
    return snapshot(transportContext).identity();
  }

  String project() {
    return claim(properties.getProjectClaim());
  }

  String environment() {
    return claim(properties.getEnvironmentClaim());
  }

  String project(McpTransportContext transportContext) {
    return snapshot(transportContext).project();
  }

  String environment(McpTransportContext transportContext) {
    return snapshot(transportContext).environment();
  }

  void requireScope(String scope) {
    if (authentication().getAuthorities().stream()
        .noneMatch(authority -> authority.getAuthority().equals("SCOPE_" + scope))) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Required scope is absent");
    }
  }

  void requireScope(McpTransportContext transportContext, String scope) {
    if (!snapshot(transportContext).authorities().contains("SCOPE_" + scope)) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Required scope is absent");
    }
  }

  McpTransportContext captureTransportContext() {
    JwtAuthenticationToken token = authentication();
    Set<String> authorities = new LinkedHashSet<>();
    token.getAuthorities().forEach(authority -> authorities.add(authority.getAuthority()));
    var snapshot =
        new CallerSnapshot(
            identity(token),
            claim(token, properties.getProjectClaim()),
            claim(token, properties.getEnvironmentClaim()),
            authorities);
    return McpTransportContext.create(Map.of(CALLER_CONTEXT_KEY, snapshot));
  }

  private CallerIdentity identity(JwtAuthenticationToken token) {
    Set<String> roles = new LinkedHashSet<>();
    token.getAuthorities().forEach(authority -> roles.add(authority.getAuthority()));
    String userId = token.getName();
    if (userId == null || userId.isBlank()) {
      userId = token.getToken().getSubject();
    }
    if (userId == null || userId.isBlank()) {
      userId = token.getToken().getClaimAsString("preferred_username");
    }
    String agent = token.getToken().getClaimAsString(properties.getAgentClaim());
    boolean human = agent == null || agent.isBlank();
    IdentityKind kind = human ? IdentityKind.HUMAN : IdentityKind.AGENT;
    if (human) agent = "human:" + userId;
    return new CallerIdentity(userId, roles, agent, kind);
  }

  private String claim(String name) {
    return claim(authentication(), name);
  }

  private static String claim(JwtAuthenticationToken token, String name) {
    String value = token.getToken().getClaimAsString(name);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Required authorization context claim is absent");
    }
    return value;
  }

  private static CallerSnapshot snapshot(McpTransportContext transportContext) {
    Object value = transportContext == null ? null : transportContext.get(CALLER_CONTEXT_KEY);
    if (value instanceof CallerSnapshot snapshot) return snapshot;
    throw new IllegalStateException("Authenticated MCP transport context is required");
  }

  private static JwtAuthenticationToken authentication() {
    Authentication current = SecurityContextHolder.getContext().getAuthentication();
    if (current instanceof JwtAuthenticationToken jwt && jwt.isAuthenticated()) return jwt;
    throw new IllegalStateException("Authenticated JWT context is required");
  }

  private record CallerSnapshot(
      CallerIdentity identity, String project, String environment, Set<String> authorities) {
    private CallerSnapshot {
      authorities = Set.copyOf(authorities);
    }
  }
}

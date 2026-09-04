package com.codinglair.taf.mcp.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

@DisplayName("OIDC audience validation")
class AudienceValidatorTest {
  private final AudienceValidator validator = new AudienceValidator("taf-mcp");

  @Nested
  @DisplayName("token acceptance")
  class TokenAcceptance {
    @Test
    @DisplayName("accepts the configured audience")
    void acceptsAudience() {
      assertThat(validator.validate(jwt(List.of("taf-mcp"))).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("rejects a token issued for another audience")
    void rejectsWrongAudience() {
      assertThat(validator.validate(jwt(List.of("other"))).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("rejects an expired token")
    void rejectsExpiredToken() {
      Instant expired = Instant.now().minusSeconds(120);
      Jwt token =
          new Jwt(
              "expired-test-token",
              expired.minusSeconds(60),
              expired,
              Map.of("alg", "none"),
              Map.of("sub", "user", "aud", List.of("taf-mcp")));

      assertThat(new JwtTimestampValidator().validate(token).hasErrors()).isTrue();
    }
  }

  private static Jwt jwt(List<String> audiences) {
    Instant now = Instant.now();
    return new Jwt(
        "opaque-test-token",
        now,
        now.plusSeconds(60),
        Map.of("alg", "none"),
        Map.of("sub", "user", "aud", audiences));
  }
}

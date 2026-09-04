package com.codinglair.taf.runtime.secret;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict, CSV-safe version-one reference to a secret provider. */
public final class SecretReference {
  public static final int MAX_LENGTH = 4096;
  private static final Pattern PROVIDER = Pattern.compile("[a-z][a-z0-9-]{1,31}");
  private static final Pattern ENV_NAME = Pattern.compile("[A-Z_][A-Z0-9_]{0,127}");
  private static final Pattern JASYPT_PAYLOAD = Pattern.compile("[A-Za-z0-9+/=_-]{16,4000}");
  private static final Pattern PROFILE = Pattern.compile("[a-z0-9][a-z0-9._/-]{0,255}");
  private static final Set<String> PROVIDERS = Set.of("env", "jasypt");

  private final String scheme;
  private final String provider;
  private final String payload;

  private SecretReference(String scheme, String provider, String payload) {
    this.scheme = scheme;
    this.provider = provider;
    this.payload = payload;
  }

  public static SecretReference parse(String value) {
    if (value == null || value.isBlank()) {
      throw invalid("reference is blank");
    }
    if (value.length() > MAX_LENGTH) {
      throw invalid("reference exceeds maximum length");
    }
    if (value.contains("${")
        || value.contains("..")
        || value.chars().anyMatch(Character::isWhitespace)) {
      throw invalid("reference contains a forbidden sequence");
    }
    if (value.startsWith("credential://")) {
      String alias = value.substring("credential://".length());
      if (!PROFILE.matcher(alias).matches()) {
        throw invalid("credential alias is malformed");
      }
      return new SecretReference("credential", "profile", alias);
    }
    if (!value.startsWith("secret://")) {
      throw invalid("unsupported reference scheme");
    }
    String remainder = value.substring("secret://".length());
    int separator = remainder.indexOf('/');
    if (separator < 1 || separator == remainder.length() - 1) {
      throw invalid("provider or payload is missing");
    }
    String provider = remainder.substring(0, separator).toLowerCase(Locale.ROOT);
    String payload = remainder.substring(separator + 1);
    if (!PROVIDER.matcher(provider).matches() || !PROVIDERS.contains(provider)) {
      throw invalid("unknown provider");
    }
    if (provider.equals("env") && !ENV_NAME.matcher(payload).matches()) {
      throw invalid("environment variable name is malformed");
    }
    if (provider.equals("jasypt") && !JASYPT_PAYLOAD.matcher(payload).matches()) {
      throw invalid("encrypted payload is malformed");
    }
    return new SecretReference("secret", provider, payload);
  }

  public String scheme() {
    return scheme;
  }

  public String provider() {
    return provider;
  }

  String payload() {
    return payload;
  }

  /** Non-reversible token suitable for diagnostics and audit correlation. */
  public String correlationToken() {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest((scheme + ":" + provider + ":" + payload).getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 16);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  String providerPayload() {
    return payload;
  }

  @Override
  public String toString() {
    return "SecretReference[scheme="
        + scheme
        + ", provider="
        + provider
        + ", token="
        + correlationToken()
        + "]";
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof SecretReference that
        && scheme.equals(that.scheme)
        && provider.equals(that.provider)
        && payload.equals(that.payload);
  }

  @Override
  public int hashCode() {
    return Objects.hash(scheme, provider, payload);
  }

  private static IllegalArgumentException invalid(String reason) {
    return new IllegalArgumentException("Invalid secret reference: " + reason);
  }
}

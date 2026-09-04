package com.codinglair.taf.api.rest;

import com.codinglair.taf.runtime.secret.SecretManager;
import com.codinglair.taf.runtime.secret.SecretRequestContext;
import io.restassured.specification.RequestSpecification;
import java.util.Objects;

/** Authentication that stores only an opaque secret reference and resolves it at execution time. */
public final class SecretReferenceAuthentication implements RestAuthentication {
  private final SecretManager secrets;
  private final String reference;
  private final SecretRequestContext context;
  private final String scheme;

  public SecretReferenceAuthentication(
      SecretManager secrets, String reference, SecretRequestContext context, String scheme) {
    this.secrets = Objects.requireNonNull(secrets);
    this.reference = requireReference(reference);
    this.context = Objects.requireNonNull(context);
    this.scheme = scheme == null ? "" : scheme.strip();
  }

  public static SecretReferenceAuthentication bearer(
      SecretManager secrets, String reference, SecretRequestContext context) {
    return new SecretReferenceAuthentication(secrets, reference, context, "Bearer");
  }

  @Override
  public void apply(RequestSpecification specification) {
    try (var resolved = secrets.resolve(reference, context)) {
      String prefix = scheme.isEmpty() ? "" : scheme + " ";
      specification.header("Authorization", prefix + resolved.useAsString());
    }
  }

  @Override
  public String toString() {
    return "SecretReferenceAuthentication[reference=REDACTED]";
  }

  private static String requireReference(String value) {
    Objects.requireNonNull(value, "reference");
    if (value.isBlank()) throw new IllegalArgumentException("Secret reference must not be blank");
    return value;
  }
}

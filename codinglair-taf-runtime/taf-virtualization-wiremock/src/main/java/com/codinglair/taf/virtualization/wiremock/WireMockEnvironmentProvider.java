package com.codinglair.taf.virtualization.wiremock;

import com.codinglair.taf.runtime.environment.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** External WireMock endpoint provider. It never owns or stops external infrastructure. */
public final class WireMockEnvironmentProvider extends AbstractEnvironmentProvider {
  public static final EnvironmentType TYPE = new EnvironmentType("wiremock");
  public static final String BASE_URL = "taf.virtualization.wiremock.base-url";

  @Override
  public String id() {
    return "wiremock-external";
  }

  @Override
  public Set<EnvironmentMode> supportedModes() {
    return Set.of(EnvironmentMode.EXTERNAL);
  }

  @Override
  public PreflightResult preflight(EnvironmentRequest request) {
    try {
      validatedUri(request);
      return result(EnvironmentStatus.READY, "External WireMock endpoint is configured", "");
    } catch (RuntimeException failure) {
      return result(
          EnvironmentStatus.MISCONFIGURED,
          "External WireMock endpoint is invalid",
          "Set " + BASE_URL + " to an absolute HTTP(S) URL");
    }
  }

  @Override
  protected EnvironmentResource create(EnvironmentRequest request) {
    URI uri = validatedUri(request);
    return new ExternalResource(UUID.randomUUID().toString(), Map.of(BASE_URL, uri.toString()));
  }

  private static String requiredUrl(EnvironmentRequest request) {
    String value = request.properties().get(BASE_URL);
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + BASE_URL);
    return value.trim();
  }

  private static URI validatedUri(EnvironmentRequest request) {
    URI uri = URI.create(requiredUrl(request));
    if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
        || uri.getHost() == null) {
      throw new IllegalArgumentException(BASE_URL + " must be an absolute HTTP(S) URL");
    }
    return uri;
  }

  private static PreflightResult result(EnvironmentStatus status, String summary, String action) {
    return PreflightResult.from(
        List.of(
            new PreflightCheckResult(
                "wiremock-external",
                PreflightCheckType.ENDPOINT,
                Optional.empty(),
                status,
                summary,
                action,
                Map.of(),
                Instant.now())));
  }

  private record ExternalResource(String id, Map<String, String> properties, AtomicBoolean closed)
      implements EnvironmentResource {
    ExternalResource(String id, Map<String, String> properties) {
      this(id, properties, new AtomicBoolean());
    }

    @Override
    public EnvironmentType type() {
      return TYPE;
    }

    @Override
    public EnvironmentMode mode() {
      return EnvironmentMode.EXTERNAL;
    }

    @Override
    public EnvironmentDiagnostic diagnose() {
      return new EnvironmentDiagnostic(
          EnvironmentStatus.READY,
          "External WireMock endpoint configured",
          "",
          Map.of(),
          Instant.now());
    }

    @Override
    public void cleanup() {
      closed.compareAndSet(false, true);
    }
  }
}

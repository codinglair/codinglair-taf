package com.codinglair.taf.virtualization.wiremock;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import com.codinglair.taf.runtime.core.reporting.ArtifactCollector;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** Thread-safe, session-scoped mapping lifecycle and request-verification API. */
public final class WireMockVirtualization implements AutoCloseable {
  private final URI baseUri;
  private final String namespace;
  private final String environmentName;
  private final WireMock client;
  private final ArtifactCollector artifacts;
  private final NetworkFaultPolicy networkFaultPolicy;
  private final CopyOnWriteArrayList<UUID> mappingIds = new CopyOnWriteArrayList<>();
  private final AtomicBoolean closed = new AtomicBoolean();

  public WireMockVirtualization(
      URI baseUri,
      String sessionId,
      String environmentName,
      ArtifactCollector artifacts,
      NetworkFaultPolicy networkFaultPolicy) {
    this.baseUri = requireHttpUri(baseUri);
    this.namespace = sanitizeNamespace(sessionId);
    this.environmentName = requireText(environmentName, "environmentName");
    this.artifacts = Objects.requireNonNull(artifacts, "artifacts");
    this.networkFaultPolicy = Objects.requireNonNull(networkFaultPolicy, "networkFaultPolicy");
    int port =
        baseUri.getPort() >= 0
            ? baseUri.getPort()
            : ("https".equals(baseUri.getScheme()) ? 443 : 80);
    this.client =
        new WireMock(baseUri.getScheme(), baseUri.getHost(), port, normalizedBasePath(baseUri));
  }

  public MappingHandle add(VirtualMapping mapping) {
    ensureOpen();
    Objects.requireNonNull(mapping, "mapping");
    FaultClassification classification = classifyAndAuthorize(mapping.faultProfile());
    String path = namespaced(mapping.path());
    try {
      MappingBuilder builder =
          request(mapping.method(), urlPathEqualTo(path))
              .withMetadata(java.util.Map.of("tafSession", namespace));
      ResponseDefinitionBuilder response =
          aResponse().withStatus(mapping.status()).withBody(mapping.body());
      mapping.headers().forEach(response::withHeader);
      switch (mapping.faultProfile()) {
        case FaultProfile.None _ -> {}
        case FaultProfile.Latency latency ->
            response.withFixedDelay(Math.toIntExact(latency.delay().toMillis()));
        case FaultProfile.Network network -> response.withFault(network.fault().wireMockFault());
      }
      StubMapping registered = client.register(builder.willReturn(response));
      mappingIds.add(registered.getId());
      evidence("mapping-added", classification, mapping.method(), path, "configured");
      return new MappingHandle(registered.getId(), baseUri.resolve(path), classification);
    } catch (RuntimeException failure) {
      throw failure(
          "add-mapping",
          "WireMock rejected the session mapping",
          "Check WireMock availability and mapping validity",
          failure);
    }
  }

  public void verify(String method, String path, int expectedCount) {
    ensureOpen();
    if (expectedCount < 0) throw new IllegalArgumentException("expectedCount must not be negative");
    String normalizedMethod = requireText(method, "method").toUpperCase(java.util.Locale.ROOT);
    String scopedPath = namespaced(path);
    try {
      client.verifyThat(expectedCount, requestedFor(normalizedMethod, urlPathEqualTo(scopedPath)));
      evidence(
          "request-verified",
          FaultClassification.NONE,
          normalizedMethod,
          scopedPath,
          "count=" + expectedCount);
    } catch (RuntimeException failure) {
      evidence(
          "request-verification-failed",
          FaultClassification.NONE,
          normalizedMethod,
          scopedPath,
          "expectedCount=" + expectedCount);
      throw failure(
          "verify-request",
          "Request count did not match",
          "Inspect the session-scoped request evidence",
          failure);
    }
  }

  public URI endpoint(String path) {
    ensureOpen();
    return baseUri.resolve(namespaced(path));
  }

  public List<UUID> activeMappingIds() {
    return List.copyOf(mappingIds);
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) return;
    RuntimeException aggregate = null;
    for (UUID id : mappingIds.reversed()) {
      try {
        client.removeStubMapping(id);
      } catch (RuntimeException failure) {
        if (aggregate == null)
          aggregate =
              failure(
                  "cleanup",
                  "One or more mappings could not be removed",
                  "Remove mappings tagged for the session",
                  failure);
        else aggregate.addSuppressed(failure);
      }
    }
    try {
      client.removeEvents(
          requestedFor(
              "ANY",
              urlPathMatching(java.util.regex.Pattern.quote("/__taf/" + namespace) + "/.*")));
    } catch (RuntimeException ignored) {
      if (aggregate == null)
        aggregate =
            failure(
                "cleanup",
                "Session request journal could not be cleaned",
                "Check WireMock admin API availability",
                ignored);
      else aggregate.addSuppressed(ignored);
    }
    mappingIds.clear();
    if (aggregate != null) throw aggregate;
  }

  private FaultClassification classifyAndAuthorize(FaultProfile profile) {
    return switch (profile) {
      case FaultProfile.None _ -> FaultClassification.NONE;
      case FaultProfile.Latency _ -> FaultClassification.SIMULATED_LATENCY;
      case FaultProfile.Network _ -> {
        if (!networkFaultPolicy.permits(environmentName))
          throw failure(
              "authorize-fault",
              "Network-failure simulation is not permitted for environment " + environmentName,
              "Enable it only through an authorized non-production environment policy",
              null);
        yield FaultClassification.SIMULATED_NETWORK_FAILURE;
      }
    };
  }

  private void evidence(
      String operation,
      FaultClassification classification,
      String method,
      String path,
      String outcome) {
    artifacts.addArtifact(
        "wiremock-" + operation + "-" + artifacts.nextSequenceNumber(),
        "service-virtualization",
        "operation="
            + operation
            + "\nclassification="
            + classification
            + "\nmethod="
            + method
            + "\npath="
            + path
            + "\noutcome="
            + outcome,
        "text/plain",
        operation);
  }

  private String namespaced(String path) {
    String value = requireText(path, "path");
    if (!value.startsWith("/") || value.contains(".."))
      throw new IllegalArgumentException("path must be absolute and must not contain '..'");
    return "/__taf/" + namespace + value;
  }

  private void ensureOpen() {
    if (closed.get()) throw new IllegalStateException("WireMock virtualization scope is closed");
  }

  private static WireMockVirtualizationException failure(
      String operation, String message, String action, Throwable cause) {
    return new WireMockVirtualizationException(operation, message, action, cause);
  }

  private static URI requireHttpUri(URI uri) {
    Objects.requireNonNull(uri, "baseUri");
    if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
        || uri.getHost() == null)
      throw new IllegalArgumentException("baseUri must be an absolute HTTP(S) URI");
    return uri;
  }

  private static String normalizedBasePath(URI uri) {
    String path = uri.getPath();
    return path == null || path.equals("/") ? "" : path;
  }

  private static String sanitizeNamespace(String value) {
    String text = requireText(value, "sessionId");
    String safe = text.replaceAll("[^A-Za-z0-9._-]", "-");
    if (safe.length() > 96) safe = safe.substring(0, 96);
    return safe;
  }

  private static String requireText(String value, String field) {
    String result = Objects.requireNonNull(value, field).trim();
    if (result.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
    return result;
  }
}

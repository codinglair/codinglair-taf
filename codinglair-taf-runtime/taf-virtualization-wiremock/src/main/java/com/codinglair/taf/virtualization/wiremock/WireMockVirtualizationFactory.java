package com.codinglair.taf.virtualization.wiremock;

import com.codinglair.taf.runtime.core.TestSession;
import com.codinglair.taf.runtime.environment.EnvironmentResource;
import java.net.URI;
import java.util.Objects;

/** Creates mapping scopes owned by a TestSession; infrastructure remains provider-owned. */
public final class WireMockVirtualizationFactory {
  private final NetworkFaultPolicy faultPolicy;

  public WireMockVirtualizationFactory(NetworkFaultPolicy faultPolicy) {
    this.faultPolicy = Objects.requireNonNull(faultPolicy, "faultPolicy");
  }

  public WireMockVirtualization create(
      TestSession session, EnvironmentResource resource, String environmentName) {
    Objects.requireNonNull(session, "session");
    Objects.requireNonNull(resource, "resource");
    if (!WireMockEnvironmentProvider.TYPE.equals(resource.type()))
      throw new IllegalArgumentException(
          "Environment resource is not WireMock: " + resource.type().name());
    String endpoint = resource.properties().get(WireMockEnvironmentProvider.BASE_URL);
    if (endpoint == null)
      throw new IllegalArgumentException(
          "WireMock resource is missing " + WireMockEnvironmentProvider.BASE_URL);
    var virtualization =
        new WireMockVirtualization(
            URI.create(endpoint),
            session.getSessionId(),
            environmentName,
            session.getArtifactCollector(),
            faultPolicy);
    session.addCleanupListener(ignored -> virtualization.close());
    return virtualization;
  }
}

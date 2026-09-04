package com.codinglair.taf.runtime.environment.spring;

import com.codinglair.taf.runtime.core.TestSession;
import com.codinglair.taf.runtime.core.controller.ControllerContext;
import com.codinglair.taf.runtime.core.controller.EnvironmentAccess;
import com.codinglair.taf.runtime.environment.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Attaches isolated resources to the owning TestSession cleanup stack. */
public final class SessionEnvironmentManager {
  private final EnvironmentRegistry registry;
  private final TafEnvironmentProperties configuration;

  public SessionEnvironmentManager(EnvironmentRegistry registry) {
    this(registry, null);
  }

  public SessionEnvironmentManager(
      EnvironmentRegistry registry, TafEnvironmentProperties configuration) {
    this.registry = Objects.requireNonNull(registry);
    this.configuration = configuration;
  }

  public EnvironmentResource provision(TestSession session, EnvironmentRequest request) {
    Objects.requireNonNull(session, "session");
    var properties = new HashMap<>(request.properties());
    properties.put(ContainerLifecycle.PROPERTY, ContainerLifecycle.ISOLATED.name());
    EnvironmentMode mode = configuration == null ? request.mode() : configuration.getMode();
    var isolated =
        new EnvironmentRequest(
            request.resourceName(),
            request.type(),
            mode,
            request.capabilities(),
            properties,
            configuration == null ? request.timeout() : configuration.getReadinessTimeout());
    EnvironmentProvider provider = registry.providerFor(isolated.type(), isolated.mode());
    EnvironmentResource resource = provider.provision(isolated);
    session.addCleanupListener(ignored -> provider.release(resource.id()));
    return resource;
  }

  /**
   * Creates a session whose controllers can access the requested, already-provisioned resources.
   */
  public TestSession createSession(List<EnvironmentRequest> requests) {
    TestSession session = TestSession.create();
    Map<String, EnvironmentResource> resources = new java.util.concurrent.ConcurrentHashMap<>();
    try {
      for (EnvironmentRequest request : List.copyOf(requests)) {
        EnvironmentResource resource = provision(session, request);
        if (resources.putIfAbsent(request.resourceName(), resource) != null) {
          throw new IllegalArgumentException(
              "Duplicate environment resource name: " + request.resourceName());
        }
      }
      EnvironmentAccess access =
          name -> {
            EnvironmentResource resource = resources.get(name);
            if (resource == null)
              throw new IllegalArgumentException("Environment resource not found: " + name);
            return new EnvironmentAccess.Resource(
                resource.id(), resource.type().name(), resource.properties());
          };
      session
          .getControllerRegistry()
          .attach(
              new ControllerContext(
                  session.getSessionId(), access, session.getArtifactCollector()));
      return session;
    } catch (RuntimeException failure) {
      try {
        session.close();
      } catch (RuntimeException cleanup) {
        failure.addSuppressed(cleanup);
      }
      throw failure;
    }
  }
}

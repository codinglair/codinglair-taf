package com.codinglair.taf.runtime.environment;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Thread-safe lifecycle base that guarantees at-most-once resource cleanup. */
public abstract class AbstractEnvironmentProvider implements EnvironmentProvider {
  private final ConcurrentMap<String, EnvironmentResource> resources = new ConcurrentHashMap<>();

  @Override
  public final EnvironmentResource provision(EnvironmentRequest request) {
    Objects.requireNonNull(request, "request");
    if (!supportedModes().contains(request.mode())) {
      throw new EnvironmentProvisioningException(
          id(),
          request,
          "Provider does not support mode " + request.mode(),
          "Select a compatible provider or mode");
    }
    try {
      EnvironmentResource resource =
          Objects.requireNonNull(create(request), "Provider returned a null resource");
      EnvironmentResource previous = resources.putIfAbsent(resource.id(), resource);
      if (previous != null) {
        resource.cleanup();
        throw new EnvironmentProvisioningException(
            id(),
            request,
            "Resource id is already active: " + resource.id(),
            "Use a unique resource id");
      }
      return resource;
    } catch (EnvironmentProvisioningException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new EnvironmentProvisioningException(
          id(),
          request,
          "Environment provisioning failed",
          "Inspect sanitized provider diagnostics",
          failure);
    }
  }

  protected abstract EnvironmentResource create(EnvironmentRequest request);

  @Override
  public final Collection<EnvironmentResource> activeResources() {
    return List.copyOf(resources.values());
  }

  @Override
  public final void release(String resourceId) {
    EnvironmentResource resource =
        resources.remove(Objects.requireNonNull(resourceId, "resourceId"));
    if (resource != null) {
      resource.cleanup();
    }
  }

  @Override
  public final void cleanup() {
    RuntimeException aggregate = null;
    for (String resourceId : List.copyOf(resources.keySet())) {
      try {
        release(resourceId);
      } catch (RuntimeException failure) {
        if (aggregate == null) {
          aggregate = new IllegalStateException("One or more environment resources failed cleanup");
        }
        aggregate.addSuppressed(failure);
      }
    }
    if (aggregate != null) {
      throw aggregate;
    }
  }
}

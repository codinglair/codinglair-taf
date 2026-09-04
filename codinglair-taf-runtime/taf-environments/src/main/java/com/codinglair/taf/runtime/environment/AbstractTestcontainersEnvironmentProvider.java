package com.codinglair.taf.runtime.environment;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

/** Base provider for governed Testcontainers resources with shared and isolated lifecycles. */
public abstract class AbstractTestcontainersEnvironmentProvider
    extends AbstractEnvironmentProvider {
  private final EnvironmentType type;
  private final ContainerDefinition definition;
  private final ContainerImagePolicy imagePolicy;
  private final ContainerLifecycleCoordinator coordinator;

  protected AbstractTestcontainersEnvironmentProvider(
      EnvironmentType type,
      ContainerDefinition definition,
      ContainerImagePolicy imagePolicy,
      ContainerLifecycleCoordinator coordinator) {
    this.type = Objects.requireNonNull(type, "type");
    this.definition = Objects.requireNonNull(definition, "definition");
    this.imagePolicy = Objects.requireNonNull(imagePolicy, "imagePolicy");
    this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
  }

  @Override
  public final Set<EnvironmentMode> supportedModes() {
    return Set.of(EnvironmentMode.CONTAINER);
  }

  @Override
  public final PreflightResult preflight(EnvironmentRequest request) {
    Objects.requireNonNull(request, "request");
    if (request.mode() != EnvironmentMode.CONTAINER) {
      return result(
          EnvironmentStatus.MISCONFIGURED,
          "Container provider selected for external mode",
          "Select the external provider or container mode",
          Map.of());
    }
    if (!type.equals(request.type())) {
      return result(
          EnvironmentStatus.MISCONFIGURED,
          "Container provider does not support the requested type",
          "Select a provider registered for " + request.type().name(),
          Map.of());
    }
    if (!imagePolicy.isAllowed(definition.image())) {
      return result(
          EnvironmentStatus.MISCONFIGURED,
          "Container image is not approved by policy",
          "Approve the configured image through the environment image policy",
          Map.of());
    }
    try {
      boolean available = DockerClientFactory.instance().isDockerAvailable();
      return available
          ? result(EnvironmentStatus.READY, "Docker is available", "", Map.of("runtime", "docker"))
          : result(
              EnvironmentStatus.UNAVAILABLE,
              "Docker is unavailable",
              "Start Docker Desktop or configure a supported Docker-compatible runtime",
              Map.of());
    } catch (RuntimeException failure) {
      return result(
          EnvironmentStatus.UNAVAILABLE,
          "Docker availability check failed",
          "Inspect Docker daemon connectivity and Testcontainers configuration",
          Map.of("cause", DiagnosticSanitizer.sanitize(failure.getMessage())));
    }
  }

  @Override
  protected final EnvironmentResource create(EnvironmentRequest request) {
    if (!type.equals(request.type())) {
      throw new IllegalArgumentException(
          "Provider " + id() + " does not support type " + request.type().name());
    }
    if (!imagePolicy.isAllowed(definition.image())) {
      throw new IllegalArgumentException("Container image is not approved by policy");
    }
    return coordinator.acquire(
        request,
        type,
        () ->
            new TestcontainersManagedContainer(
                newContainer(definition), definition, request.timeout()));
  }

  /** Vendor-native escape hatch for provider-specific GenericContainer customization. */
  protected GenericContainer<?> newContainer(ContainerDefinition definition) {
    return new GenericContainer<>(definition.image());
  }

  private static PreflightResult result(
      EnvironmentStatus status, String summary, String action, Map<String, String> details) {
    return PreflightResult.from(
        List.of(
            new PreflightCheckResult(
                "docker",
                PreflightCheckType.READINESS,
                Optional.empty(),
                status,
                summary,
                action,
                details,
                Instant.now())));
  }
}

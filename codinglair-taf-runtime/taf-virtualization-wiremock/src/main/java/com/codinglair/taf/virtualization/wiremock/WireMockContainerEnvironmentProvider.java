package com.codinglair.taf.virtualization.wiremock;

import com.codinglair.taf.runtime.environment.*;
import java.util.*;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/** Governed disposable WireMock provider with Docker-assigned host ports. */
public final class WireMockContainerEnvironmentProvider
    extends AbstractTestcontainersEnvironmentProvider {
  public static final DockerImageName DEFAULT_IMAGE =
      DockerImageName.parse("wiremock/wiremock:3.13.2");
  private static final int PORT = 8080;

  public WireMockContainerEnvironmentProvider(ContainerLifecycleCoordinator coordinator) {
    super(
        WireMockEnvironmentProvider.TYPE,
        definition(),
        ContainerImagePolicy.allow(DEFAULT_IMAGE),
        coordinator);
  }

  @Override
  public String id() {
    return "wiremock-testcontainers";
  }

  private static ContainerDefinition definition() {
    return new ContainerDefinition(
        DEFAULT_IMAGE,
        List.of(PORT),
        Map.of(),
        List.of("--disable-banner"),
        Wait.forHttp("/__admin/health").forStatusCode(200),
        Map.of(
            WireMockEnvironmentProvider.BASE_URL,
            c -> "http://" + c.getHost() + ":" + c.getMappedPort(PORT)),
        32768);
  }
}

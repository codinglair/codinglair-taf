package com.codinglair.taf.virtualization.wiremock;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.TestSession;
import com.codinglair.taf.runtime.environment.*;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.testcontainers.DockerClientFactory;

@DisplayName("WireMock container provider")
class WireMockContainerIntegrationTest {
  @Test
  @DisplayName("runs the same mapping API against a disposable random-port container")
  void containerRoundTripAndCleanup() throws Exception {
    Assumptions.assumeTrue(Boolean.getBoolean("taf.containers.enabled"));
    Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable());
    var coordinator = new ContainerLifecycleCoordinator();
    var provider = new WireMockContainerEnvironmentProvider(coordinator);
    var request =
        new EnvironmentRequest(
            "wiremock",
            WireMockEnvironmentProvider.TYPE,
            EnvironmentMode.CONTAINER,
            Set.of(),
            Map.of(),
            Duration.ofSeconds(60));
    EnvironmentResource resource = provider.provision(request);
    try (TestSession session = TestSession.create()) {
      var scope =
          new WireMockVirtualizationFactory(NetworkFaultPolicy.denyAll())
              .create(session, resource, "local");
      var mapping = scope.add(new VirtualMapping("GET", "/container", 200, "container-ok"));
      String body =
          HttpClient.newHttpClient()
              .send(
                  HttpRequest.newBuilder(mapping.endpoint()).GET().build(),
                  HttpResponse.BodyHandlers.ofString())
              .body();
      assertThat(body).isEqualTo("container-ok");
      scope.verify("GET", "/container", 1);
    } finally {
      provider.release(resource.id());
      coordinator.close();
    }
    assertThat(provider.activeResources()).isEmpty();
  }
}

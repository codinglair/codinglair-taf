package com.codinglair.taf.virtualization.wiremock;

import static org.assertj.core.api.Assertions.*;

import com.codinglair.taf.runtime.environment.*;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.*;

@DisplayName("WireMock external environment provider")
class WireMockEnvironmentProviderTest {
  private final WireMockEnvironmentProvider provider = new WireMockEnvironmentProvider();

  @Test
  @DisplayName("rejects a relative external endpoint during preflight and provisioning")
  void rejectsRelativeEndpoint() {
    EnvironmentRequest request = request("relative/path");
    assertThat(provider.preflight(request).status()).isEqualTo(EnvironmentStatus.MISCONFIGURED);
    assertThatThrownBy(() -> provider.provision(request))
        .isInstanceOf(EnvironmentProvisioningException.class)
        .hasMessageContaining("Environment provisioning failed");
  }

  @Test
  @DisplayName("provisions and idempotently releases a valid external endpoint")
  void externalLifecycle() {
    EnvironmentResource resource = provider.provision(request("http://localhost:8080"));
    assertThat(resource.mode()).isEqualTo(EnvironmentMode.EXTERNAL);
    assertThat(resource.properties())
        .containsEntry(WireMockEnvironmentProvider.BASE_URL, "http://localhost:8080");
    provider.release(resource.id());
    assertThatCode(() -> provider.release(resource.id())).doesNotThrowAnyException();
    assertThat(provider.activeResources()).isEmpty();
  }

  private static EnvironmentRequest request(String endpoint) {
    return new EnvironmentRequest(
        "wiremock",
        WireMockEnvironmentProvider.TYPE,
        EnvironmentMode.EXTERNAL,
        Set.of(),
        Map.of(WireMockEnvironmentProvider.BASE_URL, endpoint),
        Duration.ofSeconds(5));
  }
}

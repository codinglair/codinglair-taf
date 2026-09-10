package com.codinglair.taf.messaging.aws.environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.messaging.aws.common.AwsConnectionProperties;
import com.codinglair.taf.messaging.aws.common.AwsEndpointMode;
import com.codinglair.taf.messaging.aws.common.AwsOwnershipMode;
import com.codinglair.taf.messaging.aws.common.AwsProperties;
import com.codinglair.taf.runtime.environment.EnvironmentMode;
import com.codinglair.taf.runtime.environment.EnvironmentProvisioningException;
import com.codinglair.taf.runtime.environment.EnvironmentRequest;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AwsOwnershipManifestTest {
  @Test
  void returnsOnlyOwnedEntriesInReverseDependencyOrder() {
    var queue = owned("queue", 10);
    var target = owned("target", 30);
    var external =
        new AwsOwnershipManifestEntry(
            new AwsResourceDescriptor("queue", "shared", "external"),
            "run-1",
            AwsResourceCreationSource.EXTERNAL,
            AwsResourceCleanupPolicy.PRESERVE,
            100);

    assertThat(
            new AwsOwnershipManifest("run-1", List.of(queue, external, target))
                .testOwnedInCleanupOrder())
        .containsExactly(target, queue);
  }

  @Test
  void rejectsEntriesBelongingToAnotherRun() {
    assertThatThrownBy(() -> new AwsOwnershipManifest("run-2", List.of(owned("queue", 1))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void cancellationBeforeExternalProvisioningPreservesStateAndInterruption() {
    var properties = new AwsProperties();
    var profile = new AwsConnectionProperties();
    profile.setRegion("us-east-1");
    profile.setEndpointMode(AwsEndpointMode.LOCALSTACK);
    profile.setEndpointOverride(URI.create("http://localhost:4566"));
    profile.setOwnershipMode(AwsOwnershipMode.EXTERNAL);
    properties.getProfiles().put("external", profile);
    var provider = new LocalStackEnvironmentProvider(EnvironmentMode.EXTERNAL, properties);
    var request =
        new EnvironmentRequest(
            "external",
            LocalStackEnvironmentProvider.TYPE,
            EnvironmentMode.EXTERNAL,
            Set.of(),
            Map.of(),
            Duration.ofSeconds(1));

    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> provider.provision(request))
          .isInstanceOf(EnvironmentProvisioningException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      assertThat(provider.activeResources()).isEmpty();
    } finally {
      Thread.interrupted();
    }
  }

  private static AwsOwnershipManifestEntry owned(String logical, int order) {
    return new AwsOwnershipManifestEntry(
        new AwsResourceDescriptor("test", logical, logical),
        "run-1",
        AwsResourceCreationSource.TAF,
        AwsResourceCleanupPolicy.DELETE,
        order);
  }
}

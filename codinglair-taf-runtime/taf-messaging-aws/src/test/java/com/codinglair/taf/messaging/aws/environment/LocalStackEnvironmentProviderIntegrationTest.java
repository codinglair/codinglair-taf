package com.codinglair.taf.messaging.aws.environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.messaging.aws.common.AwsConnectionProperties;
import com.codinglair.taf.messaging.aws.common.AwsEndpointMode;
import com.codinglair.taf.messaging.aws.common.AwsOwnershipMode;
import com.codinglair.taf.messaging.aws.common.AwsProperties;
import com.codinglair.taf.messaging.aws.common.SqsIsolationMode;
import com.codinglair.taf.messaging.aws.eventbridge.EventBridgeControllerProperties;
import com.codinglair.taf.messaging.aws.sqs.SqsControllerProperties;
import com.codinglair.taf.runtime.environment.EnvironmentMode;
import com.codinglair.taf.runtime.environment.EnvironmentProvisioningException;
import com.codinglair.taf.runtime.environment.EnvironmentRequest;
import com.codinglair.taf.runtime.environment.EnvironmentStatus;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.sqs.SqsClient;

@EnabledIfSystemProperty(named = "taf.containers.enabled", matches = "true")
class LocalStackEnvironmentProviderIntegrationTest {
  @Test
  void provisionsReadyIsolatedResourcesAndShutsDownIdempotently() {
    AwsProperties properties = configuration();
    var provider = new LocalStackEnvironmentProvider(EnvironmentMode.CONTAINER, properties);
    var request =
        new EnvironmentRequest(
            "local",
            LocalStackEnvironmentProvider.TYPE,
            EnvironmentMode.CONTAINER,
            Set.of(),
            Map.of("owner", "run-a"),
            Duration.ofMinutes(2));

    assertThat(provider.preflight(request).status()).isEqualTo(EnvironmentStatus.READY);
    LocalStackEnvironmentResource resource =
        (LocalStackEnvironmentResource) provider.provision(request);

    assertThat(resource.diagnose().status()).isEqualTo(EnvironmentStatus.READY);
    assertThat(resource.properties()).containsKeys(LocalStackEnvironmentProvider.ENDPOINT_PROPERTY);
    assertThat(resource.effectiveConfiguration().endpoint()).isNotNull();
    assertThat(resource.ownershipManifest().entries())
        .extracting(entry -> entry.resource().type())
        .contains(
            "sqs-dlq",
            "sqs-queue",
            "sqs-redrive-policy",
            "event-bus",
            "event-rule",
            "sqs-queue-policy",
            "event-target");
    assertThat(resource.ownershipManifest().testOwnedInCleanupOrder())
        .isSortedAccordingTo(
            (left, right) -> Integer.compare(right.dependencyOrder(), left.dependencyOrder()));

    resource.cleanup();
    resource.cleanup();
    assertThat(resource.diagnose().status()).isEqualTo(EnvironmentStatus.UNAVAILABLE);
    provider.cleanup();
  }

  @Test
  void externalPreflightAndCleanupNeverMutateDeclaredResources() {
    try (var localstack =
        new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.8.1"))
            .withServices("sqs", "events")) {
      localstack.start();
      var credentials =
          StaticCredentialsProvider.create(AwsBasicCredentials.create("localstack", "localstack"));
      try (var sqs =
              SqsClient.builder()
                  .region(Region.US_EAST_1)
                  .endpointOverride(localstack.getEndpoint())
                  .credentialsProvider(credentials)
                  .build();
          var events =
              EventBridgeClient.builder()
                  .region(Region.US_EAST_1)
                  .endpointOverride(localstack.getEndpoint())
                  .credentialsProvider(credentials)
                  .build()) {
        String queueUrl =
            sqs.createQueue(builder -> builder.queueName("external-orders")).queueUrl();
        events.createEventBus(builder -> builder.name("external-orders"));
        AwsProperties properties = externalConfiguration(localstack, queueUrl);
        var provider = new LocalStackEnvironmentProvider(EnvironmentMode.EXTERNAL, properties);
        var request =
            new EnvironmentRequest(
                "external",
                LocalStackEnvironmentProvider.TYPE,
                EnvironmentMode.EXTERNAL,
                Set.of(),
                Map.of("owner", "run-external"),
                Duration.ofSeconds(30));

        assertThat(provider.preflight(request).status()).isEqualTo(EnvironmentStatus.READY);
        LocalStackEnvironmentResource resource =
            (LocalStackEnvironmentResource) provider.provision(request);
        resource.cleanup();
        provider.cleanup();

        assertThat(resource.ownershipManifest().testOwnedInCleanupOrder()).isEmpty();
        assertThat(sqs.getQueueUrl(builder -> builder.queueName("external-orders")).queueUrl())
            .isEqualTo(queueUrl);
        assertThat(events.describeEventBus(builder -> builder.name("external-orders")).name())
            .isEqualTo("external-orders");
      }
    }
  }

  @Test
  void concurrentRunsReceiveDifferentNamespacesAndEndpoints() throws Exception {
    var provider = new LocalStackEnvironmentProvider(EnvironmentMode.CONTAINER, configuration());
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> provider.provision(request("run-one")));
      var second = executor.submit(() -> provider.provision(request("run-two")));
      var firstResource = (LocalStackEnvironmentResource) first.get();
      var secondResource = (LocalStackEnvironmentResource) second.get();

      assertThat(firstResource.properties().get(LocalStackEnvironmentProvider.ENDPOINT_PROPERTY))
          .isNotEqualTo(
              secondResource.properties().get(LocalStackEnvironmentProvider.ENDPOINT_PROPERTY));
      assertThat(firstResource.ownershipManifest().entries())
          .allMatch(entry -> entry.resource().physicalId().contains("run-one"));
      assertThat(secondResource.ownershipManifest().entries())
          .allMatch(entry -> entry.resource().physicalId().contains("run-two"));
    } finally {
      provider.cleanup();
    }
  }

  @Test
  void partialProvisionFailureCleansOwnedStateAndContainer() {
    AwsProperties properties = configuration();
    properties
        .getProfiles()
        .get("local")
        .getEventbridge()
        .get("orders")
        .setTargetSqsController("missing");
    var provider = new LocalStackEnvironmentProvider(EnvironmentMode.CONTAINER, properties);

    assertThatThrownBy(() -> provider.provision(request("failed-run")))
        .isInstanceOf(EnvironmentProvisioningException.class)
        .hasMessageNotContaining("localstack");
    assertThat(provider.activeResources()).isEmpty();
  }

  private static AwsProperties configuration() {
    var properties = new AwsProperties();
    properties.setEnabled(true);
    var profile = new AwsConnectionProperties();
    profile.setRegion("us-east-1");
    profile.setEndpointMode(AwsEndpointMode.LOCALSTACK);
    profile.setOwnershipMode(AwsOwnershipMode.TEST_OWNED);
    var queue = new SqsControllerProperties();
    queue.setQueue("orders-run-a");
    queue.setDeadLetterQueue("orders-run-a-dlq");
    profile.getSqs().put("orders", queue);
    var bus = new EventBridgeControllerProperties();
    bus.setEventBus("orders-run-a");
    bus.setTargetSqsController("orders");
    bus.setTargetIdentity("orders-target");
    profile.getEventbridge().put("orders", bus);
    properties.getProfiles().put("local", profile);
    return properties;
  }

  private static EnvironmentRequest request(String owner) {
    return new EnvironmentRequest(
        "local",
        LocalStackEnvironmentProvider.TYPE,
        EnvironmentMode.CONTAINER,
        Set.of(),
        Map.of("owner", owner),
        Duration.ofMinutes(2));
  }

  private static AwsProperties externalConfiguration(
      LocalStackContainer container, String queueUrl) {
    var properties = new AwsProperties();
    var profile = new AwsConnectionProperties();
    profile.setRegion("us-east-1");
    profile.setEndpointMode(AwsEndpointMode.LOCALSTACK);
    profile.setEndpointOverride(container.getEndpoint());
    profile.setOwnershipMode(AwsOwnershipMode.EXTERNAL);
    var queue = new SqsControllerProperties();
    queue.setQueue(queueUrl);
    queue.setIsolationMode(SqsIsolationMode.EXTERNAL_SHARED);
    profile.getSqs().put("orders", queue);
    var bus = new EventBridgeControllerProperties();
    bus.setEventBus("external-orders");
    profile.getEventbridge().put("orders", bus);
    properties.getProfiles().put("external", profile);
    return properties;
  }
}

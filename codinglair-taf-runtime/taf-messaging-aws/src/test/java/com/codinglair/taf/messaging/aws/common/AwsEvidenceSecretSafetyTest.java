package com.codinglair.taf.messaging.aws.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.messaging.aws.eventbridge.EventBridgeController;
import com.codinglair.taf.messaging.aws.eventbridge.EventBridgeControllerProperties;
import com.codinglair.taf.messaging.aws.eventbridge.EventPublishRequest;
import com.codinglair.taf.messaging.aws.sqs.ReceivedSqsMessage;
import com.codinglair.taf.messaging.aws.sqs.SqsController;
import com.codinglair.taf.messaging.aws.sqs.SqsControllerProperties;
import com.codinglair.taf.messaging.aws.sqs.SqsReceiveRequest;
import com.codinglair.taf.runtime.core.controller.ControllerContext;
import com.codinglair.taf.runtime.core.controller.EnvironmentAccess;
import com.codinglair.taf.runtime.core.reporting.ArtifactCollector;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

@DisplayName("AWS evidence secret safety")
class AwsEvidenceSecretSafetyTest {
  private static final String CANARY = "canary-secret-110-006";
  private static final String RECEIPT = "canary-receipt-handle-110-006";

  @Nested
  @DisplayName("Sanitization policy")
  class SanitizationPolicy {
    @Test
    @DisplayName("redacts nested JSON, authorization, credential, endpoint, and access-key values")
    void redactsProhibitedValues() {
      String value =
          "{\"outer\":{\"password\":\""
              + CANARY
              + "\",\"authorization\":\"Bearer "
              + CANARY
              + "\",\"receiptHandle\":\""
              + RECEIPT
              + "\"},\"endpoint\":\"https://user:"
              + CANARY
              + "@example.test/path?token="
              + CANARY
              + "\",\"access\":\"AKIA1234567890123456\"}";

      AwsEvidencePayload payload = AwsEvidenceSanitizer.payload(value, 4096);

      assertThat(payload.content())
          .doesNotContain(CANARY, RECEIPT, "AKIA1234567890123456")
          .contains("[REDACTED]");
    }

    @Test
    @DisplayName("bounds malformed, binary-like, and oversized evidence while retaining a digest")
    void boundsUntrustedEvidence() {
      String value = "not-json token=" + CANARY + " binary=AAECAwQ=" + "x".repeat(4000);

      AwsEvidencePayload payload = AwsEvidenceSanitizer.payload(value, 96);

      assertThat(payload.content().getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(96);
      assertThat(payload.content()).doesNotContain(CANARY);
      assertThat(payload.sha256()).hasSize(64);
      assertThat(payload.originalBytes()).isGreaterThan(96);
      assertThat(payload.truncated()).isTrue();
    }
  }

  @Nested
  @DisplayName("Publication paths")
  class PublicationPaths {
    @Test
    @DisplayName("routes SQS message evidence through the session artifact collector")
    void publishesSqsEvidenceWithoutPayloadSecretsOrReceiptHandles() throws Exception {
      ArtifactCollector artifacts = collector();
      SqsController controller = sqs(artifacts, messageClient());

      ReceivedSqsMessage message =
          controller
              .receive(new SqsReceiveRequest(Duration.ofMillis(100), "corr-1", Map.of(), 1))
              .getFirst();
      controller.evidence(message);

      String published = artifacts.getArtifacts().getFirst().content();
      assertThat(published)
          .contains("message-1", "corr-1", "receiveCount")
          .doesNotContain(CANARY, RECEIPT);
    }

    @Test
    @DisplayName("routes EventBridge request and partial-result evidence through the collector")
    void publishesEventEvidenceWithoutNestedOrAttributeSecrets() {
      ArtifactCollector artifacts = collector();
      EventBridgeController controller = eventBridge(artifacts, acceptedEventClient());

      controller.publish(
          List.of(
              new EventPublishRequest(
                  "orders",
                  "created",
                  "{\"nested\":{\"secret\":\"" + CANARY + "\"}}",
                  List.of("arn:safe"),
                  Map.of("authorization", CANARY),
                  "corr-1",
                  "Root=" + CANARY)));

      String published = artifacts.getArtifacts().getFirst().content();
      assertThat(published).contains("orders", "corr-1", "accepted").doesNotContain(CANARY);
    }

    @Test
    @DisplayName("publishes sanitized failure evidence and exposes no raw exception cause")
    void publishesFailureWithoutSdkCause() {
      ArtifactCollector artifacts = collector();
      SqsController controller = sqs(artifacts, failingClient());

      assertThatThrownBy(
              () ->
                  controller.receive(
                      new SqsReceiveRequest(Duration.ofMillis(10), null, Map.of(), 1)))
          .isInstanceOf(AwsControllerException.class)
          .hasNoCause()
          .hasMessageNotContaining(CANARY);
      assertThat(artifacts.getArtifacts().getFirst().content())
          .contains("receive-failure", "retryable")
          .doesNotContain(CANARY);
    }

    @Test
    @DisplayName("publishes unique bounded artifacts safely under concurrent access")
    void publishesConcurrently() throws Exception {
      ArtifactCollector artifacts = collector();
      AwsEvidencePublisher publisher = new AwsEvidencePublisher(artifacts, 128);
      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        var tasks =
            java.util.stream.IntStream.range(0, 40)
                .mapToObj(
                    index ->
                        executor.submit(
                            () ->
                                publisher.publish(
                                    "SQS", "debug", Map.of("messageId", "m-" + index))))
                .toList();
        for (var task : tasks) task.get();
      }

      assertThat(artifacts.getArtifacts()).hasSize(40);
      assertThat(artifacts.getArtifacts().stream().map(value -> value.name()).distinct())
          .hasSize(40);
      assertThat(artifacts.getArtifacts()).allMatch(value -> value.content().length() < 512);
    }
  }

  private static ArtifactCollector collector() {
    return new ArtifactCollector(
        TafTest.of("aws", "AwsEvidenceSecretSafetyTest"), "session", "aws");
  }

  private static ControllerContext context(ArtifactCollector artifacts) {
    return new ControllerContext("session", EnvironmentAccess.unavailable(), artifacts);
  }

  private static AwsConnectionProperties connection() {
    AwsConnectionProperties connection = new AwsConnectionProperties();
    connection.setRegion("us-east-1");
    connection.setEndpointMode(AwsEndpointMode.LOCALSTACK);
    connection.setEndpointOverride(URI.create("http://localhost:4566"));
    connection.setOwnershipMode(AwsOwnershipMode.TEST_OWNED);
    connection.getPolicy().setOperationTimeout(Duration.ofMillis(250));
    connection.getPolicy().setPollInterval(Duration.ofMillis(10));
    connection.getPolicy().setMaximumEvidenceBytes(1024);
    return connection;
  }

  private static SqsController sqs(ArtifactCollector artifacts, SqsClient client) {
    SqsControllerProperties settings = new SqsControllerProperties();
    settings.setQueue("queue-url");
    SqsController controller =
        DefaultAwsControllers.sqs("orders", connection(), settings, () -> client);
    controller.initialize(context(artifacts));
    return controller;
  }

  private static EventBridgeController eventBridge(
      ArtifactCollector artifacts, EventBridgeClient client) {
    EventBridgeControllerProperties settings = new EventBridgeControllerProperties();
    settings.setEventBus("orders-bus");
    EventBridgeController controller =
        DefaultAwsControllers.eventbridge("events", connection(), settings, () -> client);
    controller.initialize(context(artifacts));
    return controller;
  }

  @SuppressWarnings("unchecked")
  private static SqsClient messageClient() {
    Message message =
        Message.builder()
            .messageId("message-1")
            .receiptHandle(RECEIPT)
            .body("{\"password\":\"" + CANARY + "\",\"route\":\"orders\"}")
            .messageAttributes(
                Map.of(
                    "correlationId",
                    MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue("corr-1")
                        .build(),
                    "credential",
                    MessageAttributeValue.builder().dataType("String").stringValue(CANARY).build()))
            .attributesWithStrings(Map.of("ApproximateReceiveCount", "2", "SentTimestamp", "1"))
            .build();
    return (SqsClient)
        Proxy.newProxyInstance(
            SqsClient.class.getClassLoader(),
            new Class<?>[] {SqsClient.class},
            (_, method, arguments) -> {
              if (method.getName().equals("receiveMessage"))
                return ReceiveMessageResponse.builder().messages(message).build();
              if (method.getName().equals("serviceName")) return "sqs";
              return null;
            });
  }

  @SuppressWarnings("unchecked")
  private static SqsClient failingClient() {
    return (SqsClient)
        Proxy.newProxyInstance(
            SqsClient.class.getClassLoader(),
            new Class<?>[] {SqsClient.class},
            (_, method, arguments) -> {
              if (method.getName().equals("receiveMessage"))
                throw SqsException.builder().statusCode(500).message("token=" + CANARY).build();
              if (method.getName().equals("serviceName")) return "sqs";
              return null;
            });
  }

  @SuppressWarnings("unchecked")
  private static EventBridgeClient acceptedEventClient() {
    return (EventBridgeClient)
        Proxy.newProxyInstance(
            EventBridgeClient.class.getClassLoader(),
            new Class<?>[] {EventBridgeClient.class},
            (_, method, arguments) -> {
              if (method.getName().equals("putEvents"))
                return PutEventsResponse.builder()
                    .entries(PutEventsResultEntry.builder().eventId("event-1").build())
                    .build();
              if (method.getName().equals("serviceName")) return "eventbridge";
              return null;
            });
  }
}

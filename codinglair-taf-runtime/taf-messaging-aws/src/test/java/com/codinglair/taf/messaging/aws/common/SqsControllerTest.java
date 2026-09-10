package com.codinglair.taf.messaging.aws.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.messaging.aws.sqs.*;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

@DisplayName("SQS controller")
class SqsControllerTest {
  @org.junit.jupiter.api.AfterEach
  void clearInterruption() {
    Thread.interrupted();
  }

  @Nested
  @DisplayName("Message lifecycle")
  class MessageLifecycle {
    @Test
    @DisplayName("sends correlation, restores unmatched messages, and acknowledges explicitly")
    void sendsReceivesAndAcknowledges() throws Exception {
      ScriptedSqs sdk = new ScriptedSqs();
      sdk.receives.add(
          List.of(message("other", "r-other", "other"), message("wanted", "r-wanted", "c-1")));
      SqsController controller = controller(sdk);

      controller.send(new SqsSendRequest("outgoing", Map.of("kind", "order"), "c-1"));
      List<ReceivedSqsMessage> received =
          controller.receive(new SqsReceiveRequest(Duration.ofSeconds(1), "c-1", Map.of(), 10));

      assertThat(sdk.sent.messageAttributes()).containsKey("correlationId");
      assertThat(sdk.visibilityChanges).containsEntry("r-other", 0);
      assertThat(received)
          .singleElement()
          .extracting(value -> value.message().messageId())
          .isEqualTo("wanted");
      controller.assertBody(received.getFirst(), "body-wanted");
      controller.assertAttributes(received.getFirst(), Map.of("correlationId", "c-1"));
      assertThat(controller.evidence(received.getFirst()).payload().content())
          .isEqualTo("body-wanted");
      controller.acknowledge(received.getFirst());
      assertThat(sdk.deleted).containsExactly("r-wanted");
      assertThatThrownBy(() -> controller.visibility(received.getFirst()))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("tracks bounded visibility and restores in-flight messages during cleanup")
    void visibilityAndCleanup() throws Exception {
      ScriptedSqs sdk = new ScriptedSqs();
      sdk.receives.add(List.of(message("wanted", "r-wanted", "c-1")));
      SqsController controller = controller(sdk);
      ReceivedSqsMessage received =
          controller
              .receive(new SqsReceiveRequest(Duration.ofSeconds(1), "c-1", Map.of(), 1))
              .getFirst();

      controller.changeVisibility(received, Duration.ofSeconds(7));
      assertThat(controller.visibility(received).timeout()).contains(Duration.ofSeconds(7));
      assertThatThrownBy(() -> controller.changeVisibility(received, Duration.ofHours(1)))
          .isInstanceOf(IllegalArgumentException.class);
      controller.close();

      assertThat(sdk.visibilityChanges).containsEntry("r-wanted", 0);
      assertThat(sdk.closes).isEqualTo(1);
    }

    @Test
    @DisplayName("rejects a received message owned by another controller")
    void rejectsCrossControllerReceiptHandleUse() throws Exception {
      ScriptedSqs firstSdk = new ScriptedSqs();
      firstSdk.receives.add(List.of(message("wanted", "secret-handle", "c-1")));
      SqsController first = controller(firstSdk);
      SqsController second = controller(new ScriptedSqs());
      ReceivedSqsMessage received =
          first
              .receive(new SqsReceiveRequest(Duration.ofSeconds(1), "c-1", Map.of(), 1))
              .getFirst();

      assertThatThrownBy(() -> second.acknowledge(received))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageNotContaining("secret-handle");
    }

    @Test
    @DisplayName("keeps concurrent controller sessions isolated")
    void concurrentSessionsRemainIsolated() throws Exception {
      ScriptedSqs firstSdk = new ScriptedSqs();
      ScriptedSqs secondSdk = new ScriptedSqs();
      firstSdk.receives.add(List.of(message("first", "r-first", "c-1")));
      secondSdk.receives.add(List.of(message("second", "r-second", "c-2")));
      SqsController first = controller(firstSdk);
      SqsController second = controller(secondSdk);

      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        var firstResult =
            executor.submit(
                () ->
                    first.awaitMessage(
                        new SqsReceiveRequest(Duration.ofMillis(200), "c-1", Map.of(), 1)));
        var secondResult =
            executor.submit(
                () ->
                    second.awaitMessage(
                        new SqsReceiveRequest(Duration.ofMillis(200), "c-2", Map.of(), 1)));

        assertThat(firstResult.get().message().messageId()).isEqualTo("first");
        assertThat(secondResult.get().message().messageId()).isEqualTo("second");
      }
    }
  }

  @Nested
  @DisplayName("Bounded observation")
  class BoundedObservation {
    @Test
    @DisplayName("waits for a later matching message within the budget")
    void waitsForMatch() throws Exception {
      ScriptedSqs sdk = new ScriptedSqs();
      sdk.receives.add(List.of());
      sdk.receives.add(List.of(message("wanted", "r-wanted", "c-1")));
      SqsController controller = controller(sdk);

      assertThat(
              controller
                  .awaitMessage(new SqsReceiveRequest(Duration.ofMillis(200), "c-1", Map.of(), 1))
                  .message()
                  .messageId())
          .isEqualTo("wanted");
    }

    @Test
    @DisplayName("observes the complete bounded interval for a negative assertion")
    void negativeWaitUsesFullInterval() throws Exception {
      SqsController controller = controller(new ScriptedSqs());
      long started = System.nanoTime();

      controller.assertNoMatchingMessage(
          new SqsReceiveRequest(Duration.ofMillis(120), "absent", Map.of(), 1));

      assertThat(Duration.ofNanos(System.nanoTime() - started))
          .isGreaterThanOrEqualTo(Duration.ofMillis(110));
    }

    @Test
    @DisplayName("enforces the configured receive batch bound")
    void receiveBudgetExhaustion() {
      ScriptedSqs sdk = new ScriptedSqs();
      AwsConnectionProperties connection = connection();
      connection.getPolicy().setMaximumReceiveMessages(2);
      SqsController controller = controller(sdk, connection);

      assertThatThrownBy(
              () ->
                  controller.receive(
                      new SqsReceiveRequest(Duration.ofSeconds(1), null, Map.of(), 3)))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("preserves cancellation before polling")
    void cancellation() {
      SqsController controller = controller(new ScriptedSqs());
      Thread.currentThread().interrupt();

      assertThatThrownBy(
              () ->
                  controller.awaitMessage(
                      new SqsReceiveRequest(Duration.ofMillis(120), null, Map.of(), 1)))
          .isInstanceOf(InterruptedException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }
  }

  @Test
  @DisplayName("returns approximate source and dead-letter queue diagnostics")
  void diagnostics() {
    ScriptedSqs sdk = new ScriptedSqs();
    SqsController controller = controller(sdk);

    SqsQueueDiagnostics diagnostics = controller.diagnostics();

    assertThat(diagnostics.source().available()).isEqualTo(2);
    assertThat(diagnostics.deadLetterQueue())
        .get()
        .extracting(SqsQueueCounts::inFlight)
        .isEqualTo(1L);
  }

  private static SqsController controller(ScriptedSqs sdk) {
    return controller(sdk, connection());
  }

  private static SqsController controller(ScriptedSqs sdk, AwsConnectionProperties connection) {
    SqsControllerProperties settings = new SqsControllerProperties();
    settings.setQueue("queue-url");
    settings.setDeadLetterQueue("dlq-url");
    SqsController controller =
        DefaultAwsControllers.sqs("orders", connection, settings, sdk::client);
    controller.initialize(null);
    return controller;
  }

  private static AwsConnectionProperties connection() {
    AwsConnectionProperties properties = new AwsConnectionProperties();
    properties.setRegion("us-east-1");
    properties.setEndpointMode(AwsEndpointMode.LOCALSTACK);
    properties.setEndpointOverride(URI.create("http://localhost:4566"));
    properties.setOwnershipMode(AwsOwnershipMode.TEST_OWNED);
    properties.getPolicy().setOperationTimeout(Duration.ofMillis(250));
    properties.getPolicy().setPollInterval(Duration.ofMillis(10));
    properties.getPolicy().setMaximumVisibility(Duration.ofSeconds(30));
    return properties;
  }

  private static Message message(String id, String receipt, String correlation) {
    return Message.builder()
        .messageId(id)
        .receiptHandle(receipt)
        .body("body-" + id)
        .messageAttributes(
            Map.of(
                "correlationId",
                MessageAttributeValue.builder()
                    .dataType("String")
                    .stringValue(correlation)
                    .build()))
        .attributesWithStrings(Map.of("ApproximateReceiveCount", "1", "SentTimestamp", "1"))
        .build();
  }

  private static final class ScriptedSqs {
    final Queue<List<Message>> receives = new ConcurrentLinkedQueue<>();
    final Map<String, Integer> visibilityChanges = new LinkedHashMap<>();
    final List<String> deleted = new ArrayList<>();
    SendMessageRequest sent;
    int closes;

    @SuppressWarnings("unchecked")
    SqsClient client() {
      return (SqsClient)
          Proxy.newProxyInstance(
              SqsClient.class.getClassLoader(),
              new Class<?>[] {SqsClient.class},
              (_, method, arguments) -> {
                if (method.getName().equals("sendMessage")) {
                  SendMessageRequest.Builder builder = SendMessageRequest.builder();
                  ((Consumer<SendMessageRequest.Builder>) arguments[0]).accept(builder);
                  sent = builder.build();
                  return SendMessageResponse.builder().messageId("sent-id").build();
                }
                if (method.getName().equals("receiveMessage")) {
                  List<Message> messages = receives.poll();
                  return ReceiveMessageResponse.builder()
                      .messages(messages == null ? List.of() : messages)
                      .build();
                }
                if (method.getName().equals("changeMessageVisibility")) {
                  ChangeMessageVisibilityRequest.Builder builder =
                      ChangeMessageVisibilityRequest.builder();
                  ((Consumer<ChangeMessageVisibilityRequest.Builder>) arguments[0]).accept(builder);
                  ChangeMessageVisibilityRequest request = builder.build();
                  visibilityChanges.put(request.receiptHandle(), request.visibilityTimeout());
                  return ChangeMessageVisibilityResponse.builder().build();
                }
                if (method.getName().equals("deleteMessage")) {
                  DeleteMessageRequest.Builder builder = DeleteMessageRequest.builder();
                  ((Consumer<DeleteMessageRequest.Builder>) arguments[0]).accept(builder);
                  deleted.add(builder.build().receiptHandle());
                  return DeleteMessageResponse.builder().build();
                }
                if (method.getName().equals("getQueueAttributes"))
                  return GetQueueAttributesResponse.builder()
                      .attributesWithStrings(
                          Map.of(
                              "ApproximateNumberOfMessages", "2",
                              "ApproximateNumberOfMessagesNotVisible", "1",
                              "ApproximateNumberOfMessagesDelayed", "0"))
                      .build();
                if (method.getName().equals("close")) {
                  closes++;
                  return null;
                }
                if (method.getName().equals("serviceName")) return "sqs";
                return null;
              });
    }
  }
}

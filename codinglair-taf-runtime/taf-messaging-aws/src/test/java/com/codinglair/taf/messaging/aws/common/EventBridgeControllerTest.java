package com.codinglair.taf.messaging.aws.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.messaging.aws.eventbridge.*;
import com.codinglair.taf.messaging.aws.sqs.*;
import com.codinglair.taf.runtime.core.controller.*;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.*;

@DisplayName("EventBridge controller")
class EventBridgeControllerTest {
  @Nested
  @DisplayName("Publishing")
  class Publishing {
    @Test
    @DisplayName("maps resources, trace data, correlation, evidence, and partial entry failures")
    void publishesStructuredBatch() {
      ScriptedEventBridge sdk = new ScriptedEventBridge();
      sdk.results =
          List.of(
              PutEventsResultEntry.builder().eventId("event-1").build(),
              PutEventsResultEntry.builder()
                  .errorCode("MalformedDetail")
                  .errorMessage("token=secret-value")
                  .build());
      EventBridgeController controller = controller("events", settings(null), sdk);
      List<EventPublishRequest> requests =
          List.of(
              event("correlation-1"),
              new EventPublishRequest(
                  "orders", "ignored", "{}", List.of(), Map.of(), "correlation-2", null));

      EventPublishResult result = controller.publish(requests);

      assertThat(result.entries()).extracting(EventPublishEntryResult::index).containsExactly(0, 1);
      assertThat(result.entries().getFirst().accepted()).isTrue();
      assertThat(result.entries().getLast().accepted()).isFalse();
      assertThat(result.entries().getLast().errorMessage()).doesNotContain("secret-value");
      assertThat(sdk.request.entries().getFirst().resources()).containsExactly("arn:resource:1");
      assertThat(sdk.request.entries().getFirst().traceHeader()).isEqualTo("Root=trace-1");
      assertThat(sdk.request.entries().getFirst().detail())
          .contains("\"correlationId\":\"correlation-1\"")
          .contains("\"tenant\":\"safe\"");
      assertThat(result.requestEvidence().getFirst().detail().content())
          .doesNotContain("secret-value");
    }

    @Test
    @DisplayName("rejects invalid structured envelopes before calling AWS")
    void rejectsInvalidEnvelope() {
      ScriptedEventBridge sdk = new ScriptedEventBridge();
      EventBridgeController controller = controller("events", settings(null), sdk);

      assertThatThrownBy(
              () ->
                  controller.publish(
                      List.of(
                          new EventPublishRequest(
                              "orders", "created", "not-json", Map.of(), "c-1"))))
          .isInstanceOf(AwsControllerException.class)
          .hasNoCause();
      assertThat(sdk.request).isNull();
    }
  }

  @Nested
  @DisplayName("Route composition")
  class RouteComposition {
    @Test
    @DisplayName("proves delivery through the configured SQS target and validates its schema")
    void verifiesMatchingRoute() throws Exception {
      String schema =
          """
          {"type":"object","required":["source","detail-type","detail"],
           "properties":{"detail":{"type":"object","required":["_taf"]}}}
          """;
      ScriptedEventBridge sdk = accepted();
      EventBridgeController controller = controller("events", settings(schema), sdk);
      StubSqs target = new StubSqs("orders-target", envelope("correlation-1"));

      EventRouteResult result =
          controller.verifyRoute(
              new EventRouteRequest(event("correlation-1"), Duration.ofMillis(50)), target);

      assertThat(result.targetIdentity()).isEqualTo("orders-queue");
      assertThat(result.correlationId()).isEqualTo("correlation-1");
      assertThat(result.publishResult().accepted()).isTrue();
      assertThat(target.awaited).isTrue();
    }

    @Test
    @DisplayName("observes the full bounded interval for a non-matching rule")
    void verifiesNonMatchingRoute() throws Exception {
      EventBridgeController controller = controller("events", settings(null), accepted());
      StubSqs target = new StubSqs("orders-target", null);

      EventPublishResult result =
          controller.assertNotRouted(
              new EventRouteRequest(event("correlation-1"), Duration.ofMillis(40)), target);

      assertThat(result.entries().getFirst().accepted()).isTrue();
      assertThat(target.negativeTimeout).isEqualTo(Duration.ofMillis(40));
    }

    @Test
    @DisplayName("reports target misconfiguration before publishing")
    void rejectsWrongTarget() {
      ScriptedEventBridge sdk = accepted();
      EventBridgeController controller = controller("events", settings(null), sdk);

      assertThatThrownBy(
              () ->
                  controller.verifyRoute(
                      new EventRouteRequest(event("correlation-1"), Duration.ofMillis(50)),
                      new StubSqs("wrong-target", envelope("correlation-1"))))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("does not match");
      assertThat(sdk.request).isNull();
    }

    @Test
    @DisplayName("rejects a delivered envelope that violates the configured schema")
    void rejectsSchemaMismatch() {
      String schema = "{\"type\":\"object\",\"required\":[\"missing\"]}";
      EventBridgeController controller = controller("events", settings(schema), accepted());

      assertThatThrownBy(
              () ->
                  controller.verifyRoute(
                      new EventRouteRequest(event("correlation-1"), Duration.ofMillis(50)),
                      new StubSqs("orders-target", envelope("correlation-1"))))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("schema validation failed");
    }
  }

  private static EventPublishRequest event(String correlation) {
    return new EventPublishRequest(
        "orders",
        "created",
        "{\"orderId\":\"42\",\"password\":\"secret-value\"}",
        List.of("arn:resource:1"),
        Map.of("tenant", "safe"),
        correlation,
        "Root=trace-1");
  }

  private static String envelope(String correlation) {
    return """
        {"source":"orders","detail-type":"created","resources":["arn:resource:1"],
         "detail":{"orderId":"42","_taf":{"correlationId":"%s","metadata":{"tenant":"safe"}}}}
        """
        .formatted(correlation);
  }

  private static EventBridgeControllerProperties settings(String schema) {
    var settings = new EventBridgeControllerProperties();
    settings.setEventBus("orders-bus");
    settings.setTargetSqsController("orders-target");
    settings.setTargetIdentity("orders-queue");
    settings.setEnvelopeSchema(schema);
    return settings;
  }

  private static EventBridgeController controller(
      String name, EventBridgeControllerProperties settings, ScriptedEventBridge sdk) {
    var connection = new AwsConnectionProperties();
    connection.setRegion("us-east-1");
    EventBridgeController controller =
        DefaultAwsControllers.eventbridge(name, connection, settings, () -> sdk.client());
    controller.initialize(null);
    return controller;
  }

  private static ScriptedEventBridge accepted() {
    ScriptedEventBridge sdk = new ScriptedEventBridge();
    sdk.results = List.of(PutEventsResultEntry.builder().eventId("event-1").build());
    return sdk;
  }

  private static final class ScriptedEventBridge {
    private PutEventsRequest request;
    private List<PutEventsResultEntry> results = List.of();

    EventBridgeClient client() {
      return (EventBridgeClient)
          Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class<?>[] {EventBridgeClient.class},
              (_, method, arguments) -> {
                if (method.getName().equals("putEvents")) {
                  request = request(arguments[0]);
                  return PutEventsResponse.builder().entries(results).build();
                }
                if (method.getName().equals("serviceName")) return "eventbridge";
                if (method.getName().equals("close")) return null;
                return defaultValue(method.getReturnType());
              });
    }

    @SuppressWarnings("unchecked")
    private static PutEventsRequest request(Object argument) {
      if (argument instanceof PutEventsRequest direct) return direct;
      PutEventsRequest.Builder builder = PutEventsRequest.builder();
      ((Consumer<PutEventsRequest.Builder>) argument).accept(builder);
      return builder.build();
    }
  }

  private static final class StubSqs implements SqsController {
    private final ControllerIdentity identity;
    private final String envelope;
    private boolean awaited;
    private Duration negativeTimeout;

    StubSqs(String name, String envelope) {
      identity = new ControllerIdentity(SqsController.class, name);
      this.envelope = envelope;
    }

    public ControllerIdentity identity() {
      return identity;
    }

    public ControllerState state() {
      return ControllerState.READY;
    }

    public void initialize(ControllerContext context) {}

    public HealthResult health() {
      return HealthResult.unknown("stub");
    }

    public Stream<TestArtifact> collectArtifacts(ArtifactReason reason) {
      return Stream.empty();
    }

    public SqsSendResult send(SqsSendRequest request) {
      throw new UnsupportedOperationException();
    }

    public List<ReceivedSqsMessage> receive(SqsReceiveRequest request) {
      return List.of();
    }

    public ReceivedSqsMessage awaitMessage(SqsReceiveRequest request) {
      awaited = true;
      return message(envelope);
    }

    public void assertNoMatchingMessage(SqsReceiveRequest request) {
      negativeTimeout = request.timeout();
    }

    public void assertBody(ReceivedSqsMessage message, String expectedBody) {}

    public void assertAttributes(
        ReceivedSqsMessage message, Map<String, String> expectedAttributes) {}

    public SqsMessageEvidence evidence(ReceivedSqsMessage message) {
      return new SqsMessageEvidence(
          "message-1",
          null,
          1,
          Instant.EPOCH,
          Instant.EPOCH,
          Map.of(),
          AwsEvidenceSanitizer.payload(message.message().body(), 4096));
    }

    public void acknowledge(ReceivedSqsMessage message) {}

    public void changeVisibility(ReceivedSqsMessage message, Duration visibility) {}

    public SqsVisibility visibility(ReceivedSqsMessage message) {
      throw new UnsupportedOperationException();
    }

    public SqsQueueDiagnostics diagnostics() {
      throw new UnsupportedOperationException();
    }

    public void close() {}

    private static ReceivedSqsMessage message(String body) {
      return new ReceivedSqsMessage(
          new SqsMessage("message-1", body, Map.of(), null, 1, Instant.EPOCH, Instant.EPOCH),
          "receipt-secret");
    }
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) return null;
    if (type == boolean.class) return false;
    if (type == char.class) return '\0';
    return 0;
  }
}

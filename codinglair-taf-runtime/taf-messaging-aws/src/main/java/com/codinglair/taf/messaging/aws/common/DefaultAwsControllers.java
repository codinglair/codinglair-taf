package com.codinglair.taf.messaging.aws.common;

import com.codinglair.taf.messaging.aws.eventbridge.*;
import com.codinglair.taf.messaging.aws.sqs.*;
import com.codinglair.taf.runtime.core.controller.*;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

/**
 * Internal constructors for session-owned AWS controllers. This class retains no controller
 * instances; lifecycle and cleanup belong to each returned {@link TestController} and are managed
 * by the session controller registry.
 */
final class DefaultAwsControllers {
  private DefaultAwsControllers() {}

  static SqsController sqs(
      String name, AwsConnectionProperties connection, SqsControllerProperties settings) {
    return new DefaultSqsController(
        name, connection, settings, () -> new AwsClientFactory().sqs(connection));
  }

  static SqsController sqs(
      String name,
      AwsConnectionProperties connection,
      SqsControllerProperties settings,
      Supplier<SqsClient> clientSupplier) {
    return new DefaultSqsController(name, connection, settings, clientSupplier);
  }

  static EventBridgeController eventbridge(
      String name, AwsConnectionProperties connection, EventBridgeControllerProperties settings) {
    return new DefaultEventBridgeController(
        name, connection, settings, () -> new AwsClientFactory().eventbridge(connection));
  }

  static EventBridgeController eventbridge(
      String name,
      AwsConnectionProperties connection,
      EventBridgeControllerProperties settings,
      Supplier<EventBridgeClient> clientSupplier) {
    return new DefaultEventBridgeController(name, connection, settings, clientSupplier);
  }

  private abstract static class BaseController implements TestController {
    final ControllerIdentity identity;
    final AtomicReference<ControllerState> state = new AtomicReference<>(ControllerState.NEW);

    BaseController(Class<? extends TestController> type, String name) {
      identity = new ControllerIdentity(type, name);
    }

    public ControllerIdentity identity() {
      return identity;
    }

    public ControllerState state() {
      return state.get();
    }

    public HealthResult health() {
      return state.get() == ControllerState.READY
          ? new HealthResult(
              HealthResult.Status.HEALTHY,
              "AWS client is ready",
              Map.of("controller", identity.name()))
          : HealthResult.unknown("AWS client is not ready");
    }

    public Stream<TestArtifact> collectArtifacts(ArtifactReason reason) {
      return Stream.empty();
    }

    void begin() {
      if (!state.compareAndSet(ControllerState.NEW, ControllerState.INITIALIZING))
        throw new IllegalStateException("Controller cannot initialize from " + state.get());
    }

    void ready() {
      state.set(ControllerState.READY);
    }

    void ensureReady() {
      if (state.get() != ControllerState.READY)
        throw new IllegalStateException("Controller is not ready: " + identity);
    }
  }

  private static final class DefaultSqsController extends BaseController implements SqsController {
    private final AwsConnectionProperties connection;
    private final SqsControllerProperties settings;
    private final Supplier<SqsClient> clientSupplier;
    private volatile SqsClient client;

    DefaultSqsController(
        String name,
        AwsConnectionProperties connection,
        SqsControllerProperties settings,
        Supplier<SqsClient> clientSupplier) {
      super(SqsController.class, name);
      this.connection = connection;
      this.settings = settings;
      this.clientSupplier = clientSupplier;
    }

    public void initialize(ControllerContext context) {
      begin();
      try {
        connection.validate("taf.aws.profiles.<resolved>");
        client = clientSupplier.get();
        ready();
      } catch (RuntimeException failure) {
        state.set(ControllerState.FAILED);
        throw failure("SQS", "initialize", failure);
      }
    }

    public SqsSendResult send(SqsSendRequest request) {
      ensureReady();
      try {
        var response =
            client.sendMessage(
                builder ->
                    builder
                        .queueUrl(settings.getQueue())
                        .messageBody(request.body())
                        .messageAttributes(toAttributes(request.attributes())));
        return new SqsSendResult(response.messageId(), digest(request.body()), Instant.now());
      } catch (RuntimeException failure) {
        throw failure("SQS", "send", failure);
      }
    }

    public List<ReceivedSqsMessage> receive(SqsReceiveRequest request) throws InterruptedException {
      ensureReady();
      if (Thread.currentThread().isInterrupted()) throw interrupted();
      Duration allowed =
          request.timeout().compareTo(connection.getPolicy().getOperationTimeout()) > 0
              ? connection.getPolicy().getOperationTimeout()
              : request.timeout();
      int waitSeconds = Math.toIntExact(Math.min(20, Math.max(1, allowed.toSeconds())));
      try {
        var response =
            client.receiveMessage(
                builder ->
                    builder
                        .queueUrl(settings.getQueue())
                        .maxNumberOfMessages(request.maximumMessages())
                        .waitTimeSeconds(waitSeconds)
                        .messageAttributeNames("All")
                        .messageSystemAttributeNames(
                            MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT,
                            MessageSystemAttributeName.SENT_TIMESTAMP));
        return response.messages().stream()
            .filter(message -> matches(message, request))
            .map(message -> received(message))
            .toList();
      } catch (RuntimeException failure) {
        throw failure("SQS", "receive", failure);
      }
    }

    public void acknowledge(ReceivedSqsMessage message) {
      ensureReady();
      try {
        client.deleteMessage(
            builder ->
                builder.queueUrl(settings.getQueue()).receiptHandle(message.receiptHandle()));
      } catch (RuntimeException failure) {
        throw failure("SQS", "acknowledge", failure);
      }
    }

    public void changeVisibility(ReceivedSqsMessage message, Duration visibility) {
      ensureReady();
      if (visibility.isNegative()
          || visibility.compareTo(connection.getPolicy().getMaximumVisibility()) > 0)
        throw new IllegalArgumentException("visibility is outside configured bounds");
      try {
        client.changeMessageVisibility(
            builder ->
                builder
                    .queueUrl(settings.getQueue())
                    .receiptHandle(message.receiptHandle())
                    .visibilityTimeout(Math.toIntExact(visibility.toSeconds())));
      } catch (RuntimeException failure) {
        throw failure("SQS", "change visibility", failure);
      }
    }

    public void close() {
      if (state.getAndSet(ControllerState.CLOSED) == ControllerState.CLOSED) return;
      SqsClient current = client;
      client = null;
      if (current != null) current.close();
    }

    private static Map<String, MessageAttributeValue> toAttributes(Map<String, String> attributes) {
      Map<String, MessageAttributeValue> result = new LinkedHashMap<>();
      attributes.forEach(
          (key, value) ->
              result.put(
                  key,
                  MessageAttributeValue.builder().dataType("String").stringValue(value).build()));
      return Map.copyOf(result);
    }

    private static boolean matches(Message message, SqsReceiveRequest request) {
      return request.attributes().entrySet().stream()
              .allMatch(
                  entry ->
                      message.messageAttributes().containsKey(entry.getKey())
                          && entry
                              .getValue()
                              .equals(
                                  message.messageAttributes().get(entry.getKey()).stringValue()))
          && (request.correlationId() == null
              || request.correlationId().equals(attribute(message, "correlationId")));
    }

    private static String attribute(Message message, String name) {
      MessageAttributeValue value = message.messageAttributes().get(name);
      return value == null ? null : value.stringValue();
    }

    private static ReceivedSqsMessage received(Message value) {
      int count =
          Integer.parseInt(
              value.attributesAsStrings().getOrDefault("ApproximateReceiveCount", "1"));
      Map<String, String> attributes = new LinkedHashMap<>();
      value.messageAttributes().forEach((key, item) -> attributes.put(key, item.stringValue()));
      return new ReceivedSqsMessage(
          new SqsMessage(
              value.messageId(),
              value.body(),
              attributes,
              attribute(value, "correlationId"),
              count,
              Instant.now()),
          value.receiptHandle());
    }
  }

  private static final class DefaultEventBridgeController extends BaseController
      implements EventBridgeController {
    private final AwsConnectionProperties connection;
    private final EventBridgeControllerProperties settings;
    private final Supplier<EventBridgeClient> clientSupplier;
    private volatile EventBridgeClient client;

    DefaultEventBridgeController(
        String name,
        AwsConnectionProperties connection,
        EventBridgeControllerProperties settings,
        Supplier<EventBridgeClient> clientSupplier) {
      super(EventBridgeController.class, name);
      this.connection = connection;
      this.settings = settings;
      this.clientSupplier = clientSupplier;
    }

    public void initialize(ControllerContext context) {
      begin();
      try {
        connection.validate("taf.aws.profiles.<resolved>");
        client = clientSupplier.get();
        ready();
      } catch (RuntimeException failure) {
        state.set(ControllerState.FAILED);
        throw failure("EventBridge", "initialize", failure);
      }
    }

    public EventPublishResult publish(List<EventPublishRequest> events) {
      ensureReady();
      if (events == null || events.isEmpty() || events.size() > 10)
        throw new IllegalArgumentException("events must contain between 1 and 10 entries");
      try {
        var entries =
            events.stream()
                .map(
                    event ->
                        PutEventsRequestEntry.builder()
                            .eventBusName(settings.getEventBus())
                            .source(event.source())
                            .detailType(event.detailType())
                            .detail(event.detail())
                            .build())
                .toList();
        var response = client.putEvents(PutEventsRequest.builder().entries(entries).build());
        List<EventPublishEntryResult> results = new ArrayList<>();
        for (int index = 0; index < response.entries().size(); index++) {
          var value = response.entries().get(index);
          results.add(
              new EventPublishEntryResult(
                  index,
                  value.errorCode() == null,
                  value.eventId(),
                  value.errorCode(),
                  value.errorMessage()));
        }
        return new EventPublishResult(results, Instant.now());
      } catch (RuntimeException failure) {
        throw failure("EventBridge", "publish", failure);
      }
    }

    public void close() {
      if (state.getAndSet(ControllerState.CLOSED) == ControllerState.CLOSED) return;
      EventBridgeClient current = client;
      client = null;
      if (current != null) current.close();
    }
  }

  private static String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }

  private static InterruptedException interrupted() {
    Thread.currentThread().interrupt();
    return new InterruptedException("SQS receive interrupted");
  }

  private static AwsControllerException failure(
      String service, String operation, RuntimeException failure) {
    return failure instanceof AwsControllerException existing
        ? existing
        : new AwsControllerException(service, operation, failure);
  }
}

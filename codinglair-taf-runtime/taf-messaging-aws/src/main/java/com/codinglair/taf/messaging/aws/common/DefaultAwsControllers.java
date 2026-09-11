package com.codinglair.taf.messaging.aws.common;

import com.codinglair.taf.messaging.aws.eventbridge.*;
import com.codinglair.taf.messaging.aws.sqs.*;
import com.codinglair.taf.runtime.core.controller.*;
import com.codinglair.taf.runtime.core.reporting.ArtifactCollector;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
    private volatile AwsEvidencePublisher evidencePublisher;

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

    void attachEvidence(ControllerContext context, int maximumBytes) {
      ArtifactCollector collector = context == null ? null : context.artifacts();
      evidencePublisher =
          new AwsEvidencePublisher(collector, Math.max(0, Math.min(1_048_576, maximumBytes)));
    }

    void publish(String service, String operation, Map<String, ?> evidence) {
      AwsEvidencePublisher publisher = evidencePublisher;
      if (publisher != null) publisher.publish(service, operation, evidence);
    }

    AwsControllerException publishFailure(
        String service, String operation, RuntimeException failure) {
      AwsControllerException safe = failure(service, operation, failure);
      AwsEvidencePublisher publisher = evidencePublisher;
      if (publisher != null) publisher.failure(service, operation, safe);
      return safe;
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
    private final Map<ReceivedSqsMessage, SqsVisibility> inFlight = new ConcurrentHashMap<>();
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
      attachEvidence(context, connection.getPolicy().getMaximumEvidenceBytes());
      try {
        connection.validate("taf.aws.profiles.<resolved>");
        client = clientSupplier.get();
        ready();
      } catch (RuntimeException failure) {
        state.set(ControllerState.FAILED);
        throw publishFailure("SQS", "initialize", failure);
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
                        .messageAttributes(toAttributes(sendAttributes(request))));
        SqsSendResult result =
            new SqsSendResult(response.messageId(), digest(request.body()), Instant.now());
        publish(
            "SQS",
            "send",
            Map.of(
                "messageId", result.messageId(),
                "bodyDigest", result.bodyDigest(),
                "sentAt", result.sentAt().toString()));
        return result;
      } catch (RuntimeException failure) {
        throw publishFailure("SQS", "send", failure);
      }
    }

    public List<ReceivedSqsMessage> receive(SqsReceiveRequest request) throws InterruptedException {
      ensureReady();
      Objects.requireNonNull(request, "request");
      if (request.maximumMessages() > connection.getPolicy().getMaximumReceiveMessages())
        throw new IllegalArgumentException(
            "maximumMessages exceeds the configured administrative bound");
      if (Thread.currentThread().isInterrupted()) throw interrupted();
      Duration allowed =
          request.timeout().compareTo(connection.getPolicy().getOperationTimeout()) > 0
              ? connection.getPolicy().getOperationTimeout()
              : request.timeout();
      int waitSeconds = Math.toIntExact(Math.min(20, Math.max(0, allowed.toSeconds())));
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
        List<ReceivedSqsMessage> matches = new ArrayList<>();
        for (Message sdkMessage : response.messages()) {
          ReceivedSqsMessage received = AwsResponseMapper.message(sdkMessage, Instant.now());
          if (matches(sdkMessage, request)) {
            inFlight
                .keySet()
                .removeIf(
                    existing ->
                        Objects.equals(
                            existing.message().messageId(), received.message().messageId()));
            inFlight.put(
                received, new SqsVisibility(Optional.empty(), received.message().receivedAt()));
            matches.add(received);
          } else {
            restore(sdkMessage.receiptHandle());
          }
        }
        return List.copyOf(matches);
      } catch (RuntimeException failure) {
        throw publishFailure("SQS", "receive", failure);
      }
    }

    public ReceivedSqsMessage awaitMessage(SqsReceiveRequest request) throws InterruptedException {
      long deadline = deadline(request.timeout());
      do {
        List<ReceivedSqsMessage> messages = receive(remainingRequest(request, deadline));
        if (!messages.isEmpty()) return messages.getFirst();
        pause(deadline);
      } while (System.nanoTime() < deadline);
      publish(
          "SQS",
          "await timeout",
          Map.of("correlationId", AwsEvidenceSanitizer.sanitize(request.correlationId())));
      throw new AssertionError("No matching SQS message arrived within the bounded interval");
    }

    public void assertNoMatchingMessage(SqsReceiveRequest request) throws InterruptedException {
      long deadline = deadline(request.timeout());
      do {
        List<ReceivedSqsMessage> messages = receive(remainingRequest(request, deadline));
        if (!messages.isEmpty()) {
          publish("SQS", "negative assertion failure", Map.of("matchedMessages", messages.size()));
          throw new AssertionError("A matching SQS message arrived during the bounded interval");
        }
        pause(deadline);
      } while (System.nanoTime() < deadline);
    }

    public void assertBody(ReceivedSqsMessage message, String expectedBody) {
      requireOwned(message);
      if (!Objects.equals(message.message().body(), expectedBody)) {
        publish(
            "SQS",
            "body assertion failure",
            Map.of("messageId", AwsEvidenceSanitizer.sanitize(message.message().messageId())));
        throw new AssertionError("SQS message body did not match the expected value");
      }
    }

    public void assertAttributes(
        ReceivedSqsMessage message, Map<String, String> expectedAttributes) {
      requireOwned(message);
      Objects.requireNonNull(expectedAttributes, "expectedAttributes");
      if (!message.message().attributes().entrySet().containsAll(expectedAttributes.entrySet())) {
        publish(
            "SQS",
            "attribute assertion failure",
            Map.of("messageId", AwsEvidenceSanitizer.sanitize(message.message().messageId())));
        throw new AssertionError("SQS message attributes did not contain the expected values");
      }
    }

    public SqsMessageEvidence evidence(ReceivedSqsMessage message) {
      requireOwned(message);
      SqsMessage value = message.message();
      SqsMessageEvidence evidence =
          new SqsMessageEvidence(
              value.messageId(),
              value.correlationId(),
              value.receiveCount(),
              value.sentAt(),
              value.receivedAt(),
              AwsEvidenceSanitizer.attributes(value.attributes()),
              AwsEvidenceSanitizer.payload(
                  value.body(), connection.getPolicy().getMaximumEvidenceBytes()));
      publish("SQS", "message", sqsEvidence(evidence));
      return evidence;
    }

    public void acknowledge(ReceivedSqsMessage message) {
      ensureReady();
      requireOwned(message);
      try {
        client.deleteMessage(
            builder ->
                builder.queueUrl(settings.getQueue()).receiptHandle(message.receiptHandle()));
        inFlight.remove(message);
      } catch (RuntimeException failure) {
        throw publishFailure("SQS", "acknowledge", failure);
      }
    }

    public void changeVisibility(ReceivedSqsMessage message, Duration visibility) {
      ensureReady();
      requireOwned(message);
      Objects.requireNonNull(visibility, "visibility");
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
        inFlight.put(message, new SqsVisibility(Optional.of(visibility), Instant.now()));
      } catch (RuntimeException failure) {
        throw publishFailure("SQS", "change visibility", failure);
      }
    }

    public SqsVisibility visibility(ReceivedSqsMessage message) {
      ensureReady();
      return requireOwned(message);
    }

    public SqsQueueDiagnostics diagnostics() {
      ensureReady();
      try {
        SqsQueueCounts source = counts(settings.getQueue());
        Optional<SqsQueueCounts> dlq =
            settings.getDeadLetterQueue() == null
                ? Optional.empty()
                : Optional.of(counts(settings.getDeadLetterQueue()));
        SqsQueueDiagnostics result = new SqsQueueDiagnostics(source, dlq);
        publish(
            "SQS",
            "diagnostics",
            Map.of(
                "sourceQueue", AwsEvidenceSanitizer.sanitize(source.queue()),
                "available", source.available(),
                "inFlight", source.inFlight(),
                "delayed", source.delayed(),
                "collectedAt", source.collectedAt().toString()));
        return result;
      } catch (RuntimeException failure) {
        throw publishFailure("SQS", "diagnostics", failure);
      }
    }

    public void close() {
      if (state.getAndSet(ControllerState.CLOSED) == ControllerState.CLOSED) return;
      SqsClient current = client;
      RuntimeException cleanupFailure = null;
      if (current != null) {
        for (ReceivedSqsMessage message : List.copyOf(inFlight.keySet())) {
          try {
            current.changeMessageVisibility(
                builder ->
                    builder
                        .queueUrl(settings.getQueue())
                        .receiptHandle(message.receiptHandle())
                        .visibilityTimeout(0));
            inFlight.remove(message);
          } catch (RuntimeException failure) {
            if (messageNoLongerInFlight(failure)) inFlight.remove(message);
            else if (cleanupFailure == null) cleanupFailure = failure;
            else cleanupFailure.addSuppressed(failure);
          }
        }
        current.close();
      }
      client = null;
      if (cleanupFailure != null) throw publishFailure("SQS", "cleanup visibility", cleanupFailure);
    }

    private static boolean messageNoLongerInFlight(RuntimeException failure) {
      return failure instanceof SqsException sqs
          && sqs.statusCode() == 400
          && ("ReceiptHandleIsInvalid".equals(sqs.awsErrorDetails().errorCode())
              || "InvalidParameterValue".equals(sqs.awsErrorDetails().errorCode()));
    }

    private SqsVisibility requireOwned(ReceivedSqsMessage message) {
      Objects.requireNonNull(message, "message");
      SqsVisibility visibility = inFlight.get(message);
      if (visibility == null)
        throw new IllegalArgumentException(
            "message is not an unacknowledged message owned by this controller session");
      return visibility;
    }

    private void restore(String receiptHandle) {
      client.changeMessageVisibility(
          builder ->
              builder
                  .queueUrl(settings.getQueue())
                  .receiptHandle(receiptHandle)
                  .visibilityTimeout(0));
    }

    private SqsQueueCounts counts(String queue) {
      var response =
          client.getQueueAttributes(
              builder ->
                  builder
                      .queueUrl(queue)
                      .attributeNames(
                          QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                          QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE,
                          QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED));
      Map<String, String> values = response.attributesAsStrings();
      return new SqsQueueCounts(
          queue,
          count(values, "ApproximateNumberOfMessages"),
          count(values, "ApproximateNumberOfMessagesNotVisible"),
          count(values, "ApproximateNumberOfMessagesDelayed"),
          Instant.now());
    }

    private static long count(Map<String, String> values, String name) {
      return Long.parseLong(values.getOrDefault(name, "0"));
    }

    private static Map<String, String> sendAttributes(SqsSendRequest request) {
      if (request.correlationId() == null) return request.attributes();
      Map<String, String> result = new LinkedHashMap<>(request.attributes());
      result.put("correlationId", request.correlationId());
      return Map.copyOf(result);
    }

    private static long deadline(Duration timeout) {
      long nanos = timeout.toNanos();
      long now = System.nanoTime();
      return nanos > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + nanos;
    }

    private static SqsReceiveRequest remainingRequest(SqsReceiveRequest request, long deadline) {
      long remaining = Math.max(1, deadline - System.nanoTime());
      return new SqsReceiveRequest(
          Duration.ofNanos(remaining),
          request.correlationId(),
          request.attributes(),
          request.maximumMessages());
    }

    private void pause(long deadline) throws InterruptedException {
      long remaining = deadline - System.nanoTime();
      if (remaining <= 0) return;
      Duration interval = connection.getPolicy().getPollInterval();
      Thread.sleep(
          Math.min(interval.toMillis(), Math.max(1, Duration.ofNanos(remaining).toMillis())));
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
      return AwsAttributeMatcher.matches(
          request.correlationId(),
          request.attributes(),
          attribute(message, "correlationId"),
          AwsResponseMapper.attributes(message));
    }

    private static String attribute(Message message, String name) {
      MessageAttributeValue value = message.messageAttributes().get(name);
      return value == null ? null : value.stringValue();
    }
  }

  private static final class DefaultEventBridgeController extends BaseController
      implements EventBridgeController {
    private final AwsConnectionProperties connection;
    private final EventBridgeControllerProperties settings;
    private final Supplier<EventBridgeClient> clientSupplier;
    private final EventEnvelopeValidator envelopes;
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
      this.envelopes = new EventEnvelopeValidator(settings.getEnvelopeSchema());
    }

    public void initialize(ControllerContext context) {
      begin();
      attachEvidence(context, connection.getPolicy().getMaximumEvidenceBytes());
      try {
        connection.validate("taf.aws.profiles.<resolved>");
        client = clientSupplier.get();
        ready();
      } catch (RuntimeException failure) {
        state.set(ControllerState.FAILED);
        throw publishFailure("EventBridge", "initialize", failure);
      }
    }

    public EventPublishResult publish(List<EventPublishRequest> events) {
      ensureReady();
      if (events == null || events.isEmpty() || events.size() > 10)
        throw new IllegalArgumentException("events must contain between 1 and 10 entries");
      try {
        List<String> details = events.stream().map(envelopes::detail).toList();
        List<PutEventsRequestEntry> entries = new ArrayList<>();
        for (int index = 0; index < events.size(); index++) {
          EventPublishRequest event = events.get(index);
          entries.add(
              PutEventsRequestEntry.builder()
                  .eventBusName(settings.getEventBus())
                  .source(event.source())
                  .detailType(event.detailType())
                  .detail(details.get(index))
                  .resources(event.resources())
                  .traceHeader(event.traceHeader())
                  .build());
        }
        var response = client.putEvents(PutEventsRequest.builder().entries(entries).build());
        List<EventPublishEntryResult> results = new ArrayList<>();
        List<EventPublishEvidence> evidence = new ArrayList<>();
        for (int index = 0; index < events.size(); index++) {
          if (index < response.entries().size())
            results.add(AwsResponseMapper.eventEntry(index, response.entries().get(index)));
          else
            results.add(
                new EventPublishEntryResult(
                    index, false, null, "MissingResult", "AWS returned no result for this entry"));
          EventPublishRequest event = events.get(index);
          evidence.add(
              new EventPublishEvidence(
                  index,
                  event.source(),
                  event.detailType(),
                  event.resources(),
                  AwsEvidenceSanitizer.attributes(event.metadata()),
                  event.correlationId(),
                  AwsEvidenceSanitizer.payload(
                      envelopes.sanitizedDetail(details.get(index)),
                      connection.getPolicy().getMaximumEvidenceBytes())));
        }
        EventPublishResult result = new EventPublishResult(results, evidence, Instant.now());
        for (EventPublishEvidence item : evidence)
          publish("EventBridge", "publish", eventEvidence(item, results.get(item.index())));
        return result;
      } catch (RuntimeException failure) {
        throw publishFailure("EventBridge", "publish", failure);
      }
    }

    public EventRouteResult verifyRoute(EventRouteRequest request, SqsController target)
        throws InterruptedException {
      requireRouteConfiguration(request, target);
      EventPublishResult published = publish(List.of(request.event()));
      EventPublishEntryResult entry = requireAccepted(published);
      ReceivedSqsMessage message =
          target.awaitMessage(new SqsReceiveRequest(request.timeout(), null, Map.of(), 10));
      var envelope =
          envelopes.validateTargetEnvelope(
              message.message().body(), request.event().correlationId());
      envelopes.assertExpected(envelope, request.event());
      return new EventRouteResult(
          entry,
          settings.getTargetIdentity(),
          request.event().correlationId(),
          target.evidence(message));
    }

    public EventPublishResult assertNotRouted(EventRouteRequest request, SqsController target)
        throws InterruptedException {
      requireRouteConfiguration(request, target);
      EventPublishResult published = publish(List.of(request.event()));
      requireAccepted(published);
      target.assertNoMatchingMessage(new SqsReceiveRequest(request.timeout(), null, Map.of(), 10));
      return published;
    }

    private void requireRouteConfiguration(EventRouteRequest request, SqsController target) {
      ensureReady();
      Objects.requireNonNull(request, "request");
      Objects.requireNonNull(target, "target");
      if (settings.getTargetSqsController() == null)
        throw new IllegalStateException(
            "EventBridge route verification requires a configured target SQS controller and target identity");
      if (!settings.getTargetSqsController().equals(target.identity().name()))
        throw new IllegalArgumentException(
            "SQS target controller does not match the configured EventBridge route target");
      if (request.event().correlationId() == null)
        throw new IllegalArgumentException("Route verification requires a correlation identity");
    }

    private static EventPublishEntryResult requireAccepted(EventPublishResult published) {
      EventPublishEntryResult entry = published.entries().getFirst();
      if (!entry.accepted())
        throw new AssertionError(
            "EventBridge rejected the route-verification event: " + entry.errorCode());
      return entry;
    }

    public void close() {
      if (state.getAndSet(ControllerState.CLOSED) == ControllerState.CLOSED) return;
      EventBridgeClient current = client;
      client = null;
      if (current != null) current.close();
    }
  }

  private static Map<String, ?> sqsEvidence(SqsMessageEvidence value) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("messageId", value.messageId());
    result.put("correlationId", value.correlationId());
    result.put("receiveCount", value.receiveCount());
    result.put("sentAt", value.sentAt() == null ? null : value.sentAt().toString());
    result.put("receivedAt", value.receivedAt().toString());
    result.put("attributes", value.attributes());
    result.put("payload", payloadEvidence(value.payload()));
    return result;
  }

  private static Map<String, ?> eventEvidence(
      EventPublishEvidence value, EventPublishEntryResult entry) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("index", value.index());
    result.put("source", AwsEvidenceSanitizer.sanitize(value.source()));
    result.put("detailType", AwsEvidenceSanitizer.sanitize(value.detailType()));
    result.put(
        "resources", value.resources().stream().map(AwsEvidenceSanitizer::sanitize).toList());
    result.put("metadata", value.metadata());
    result.put("correlationId", AwsEvidenceSanitizer.sanitize(value.correlationId()));
    result.put("detail", payloadEvidence(value.detail()));
    result.put("accepted", entry.accepted());
    result.put("eventId", entry.eventId());
    result.put("errorCode", AwsEvidenceSanitizer.sanitize(entry.errorCode()));
    result.put("errorMessage", AwsEvidenceSanitizer.sanitize(entry.errorMessage()));
    return result;
  }

  private static Map<String, ?> payloadEvidence(AwsEvidencePayload value) {
    return Map.of(
        "content", value.content(),
        "sha256", value.sha256(),
        "originalBytes", value.originalBytes(),
        "truncated", value.truncated());
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

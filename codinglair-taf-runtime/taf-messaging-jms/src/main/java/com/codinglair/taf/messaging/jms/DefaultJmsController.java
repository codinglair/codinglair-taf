package com.codinglair.taf.messaging.jms;

import com.codinglair.taf.messaging.*;
import com.codinglair.taf.runtime.core.controller.*;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import jakarta.jms.BytesMessage;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.JMSRuntimeException;
import jakarta.jms.Message;
import jakarta.jms.Topic;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

final class DefaultJmsController implements JmsController {
  private static final String CORRELATION_NAME = "TAF_CORRELATION_NAME";
  private static final String HEADER_PREFIX = "TAF_HEADER_";
  private final ControllerIdentity identity;
  private final JmsControllerSettings settings;
  private final JmsConnectionFactoryProvider provider;
  private final AtomicReference<ControllerState> state = new AtomicReference<>(ControllerState.NEW);
  private final Set<String> durableSubscriptions = ConcurrentHashMap.newKeySet();
  private final Map<String, MessageRecord> published = new ConcurrentHashMap<>();
  private final Map<String, Object> destinationLocks = new ConcurrentHashMap<>();
  private final Map<String, Deque<MessageRecord>> buffered = new ConcurrentHashMap<>();
  private final Deque<String> evidence = new ConcurrentLinkedDeque<>();
  private volatile ConnectionFactory connectionFactory;

  DefaultJmsController(
      String name, JmsControllerSettings settings, JmsConnectionFactoryProvider provider) {
    identity = new ControllerIdentity(JmsController.class, name);
    this.settings = Objects.requireNonNull(settings, "settings");
    this.provider = Objects.requireNonNull(provider, "provider");
  }

  @Override
  public ControllerIdentity identity() {
    return identity;
  }

  @Override
  public ControllerState state() {
    return state.get();
  }

  @Override
  public void initialize(ControllerContext context) {
    if (!state.compareAndSet(ControllerState.NEW, ControllerState.INITIALIZING))
      throw new IllegalStateException("Controller cannot initialize from " + state.get());
    try {
      settings.validate("taf.messaging.jms.controllers." + identity.name());
      ConnectionFactory created =
          Objects.requireNonNull(
              provider.create(identity.name(), settings, context), "connectionFactory");
      try (JMSContext probe = created.createContext()) {
        configureClientId(probe);
        probe.getMetaData().getJMSProviderName();
      }
      connectionFactory = created;
      state.set(ControllerState.READY);
    } catch (JMSException | RuntimeException failure) {
      state.set(ControllerState.FAILED);
      throw failure("initialize", "verify provider configuration and broker readiness", failure);
    }
  }

  @Override
  public HealthResult health() {
    if (state.get() != ControllerState.READY)
      return new HealthResult(
          HealthResult.Status.UNAVAILABLE,
          "JMS controller is not ready",
          Map.of("state", state.get().name()));
    try (JMSContext context = connectionFactory.createContext()) {
      return new HealthResult(
          HealthResult.Status.HEALTHY,
          "JMS provider is reachable",
          Map.of("provider", safe(context.getMetaData().getJMSProviderName())));
    } catch (JMSException | RuntimeException failure) {
      return new HealthResult(
          HealthResult.Status.UNAVAILABLE,
          "JMS provider is unavailable",
          Map.of("cause", failure.getClass().getSimpleName()));
    }
  }

  @Override
  public MessageRecord publish(String destination, MessageEnvelope message) {
    return publish(JmsDestination.queue(destination), message);
  }

  @Override
  public MessageRecord publish(JmsDestination destination, MessageEnvelope envelope) {
    ensureReady();
    Objects.requireNonNull(destination, "destination");
    Objects.requireNonNull(envelope, "message");
    try (JMSContext context = connectionFactory.createContext()) {
      BytesMessage message = context.createBytesMessage();
      message.writeBytes(envelope.payload());
      for (var header : envelope.headers().entrySet())
        message.setStringProperty(encodeHeaderName(header.getKey()), header.getValue());
      if (envelope.correlation().isPresent()) {
        Correlation value = envelope.correlation().orElseThrow();
        message.setJMSCorrelationID(value.value());
        message.setStringProperty(CORRELATION_NAME, value.name());
      }
      context.createProducer().send(resolve(context, destination), message);
      MessageRecord record = record(destination, envelope, message, false);
      published.put(message.getJMSMessageID(), record);
      remember("publish", destination, null);
      return record;
    } catch (JMSRuntimeException | JMSException failure) {
      throw failure(
          "publish", "verify destination, authorization, and provider availability", failure);
    }
  }

  @Override
  public ConsumptionResult consume(MessageQuery query) throws InterruptedException {
    return consume(JmsDestination.queue(query.destination()), null, query);
  }

  @Override
  public ConsumptionResult consume(JmsDestination destination, String selector, MessageQuery query)
      throws InterruptedException {
    return receive(destination, selector, null, query);
  }

  @Override
  public ConsumptionResult consumeDurable(
      JmsDestination topic, JmsSubscription subscription, MessageQuery query)
      throws InterruptedException {
    Objects.requireNonNull(subscription, "subscription");
    if (topic.type() != JmsDestination.Type.TOPIC)
      throw new IllegalArgumentException("durable subscriptions require a topic");
    if (settings.getClientId() == null || settings.getClientId().isBlank())
      throw new IllegalStateException("client-id is required for durable subscriptions");
    durableSubscriptions.add(subscription.name());
    return receive(topic, subscription.selector(), subscription.name(), query);
  }

  private ConsumptionResult receive(
      JmsDestination destination, String selector, String durableName, MessageQuery query)
      throws InterruptedException {
    ensureReady();
    Objects.requireNonNull(destination, "destination");
    Objects.requireNonNull(query, "query");
    long started = System.nanoTime();
    synchronized (
        destinationLocks.computeIfAbsent(
            destinationKey(destination, durableName), ignored -> new Object())) {
      MessageRecord cached = removeMatch(destination, durableName, query);
      if (cached != null) return ConsumptionResult.matched(cached, elapsed(started));
      try (JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
        configureClientId(context);
        Destination nativeDestination = resolve(context, destination);
        try (JMSConsumer consumer =
            durableName == null
                ? context.createConsumer(nativeDestination, normalize(selector))
                : context.createDurableConsumer(
                    (Topic) nativeDestination, durableName, normalize(selector), false)) {
          while (elapsed(started).compareTo(query.timeout()) < 0) {
            if (Thread.currentThread().isInterrupted()) throw interrupted();
            long remaining = Math.max(1, query.timeout().minus(elapsed(started)).toMillis());
            Message message = consumer.receive(Math.min(remaining, 100));
            if (message == null) continue;
            MessageRecord record = fromNative(destination, message);
            if (query.selector().matches(record)) {
              remember(
                  durableName == null ? "consume" : "consume-durable", destination, durableName);
              return ConsumptionResult.matched(record, elapsed(started));
            }
            Deque<MessageRecord> records =
                buffered.computeIfAbsent(
                    destinationKey(destination, durableName), ignored -> new ArrayDeque<>());
            if (records.size() >= settings.getMaximumEvidenceRecords())
              throw failure(
                  "buffer unmatched message",
                  "narrow the selector or increase maximum-evidence-records",
                  null);
            records.addLast(record);
          }
          return ConsumptionResult.noMatch(elapsed(started));
        }
      } catch (JMSRuntimeException | JMSException failure) {
        if (Thread.currentThread().isInterrupted()) throw interrupted();
        throw failure(
            "consume",
            "verify selector, subscription, destination, and provider availability",
            failure);
      }
    }
  }

  @Override
  public Stream<TestArtifact> collectArtifacts(ArtifactReason reason) {
    if (evidence.isEmpty()) return Stream.empty();
    return Stream.of(
        TestArtifact.of(
            "jms-metadata.txt",
            "messaging-diagnostic",
            String.join(System.lineSeparator(), evidence),
            "text/plain"));
  }

  @Override
  public void close() {
    if (state.getAndSet(ControllerState.CLOSED) == ControllerState.CLOSED) return;
    ConnectionFactory factory = connectionFactory;
    if (factory != null && !durableSubscriptions.isEmpty()) {
      try (JMSContext context = factory.createContext()) {
        configureClientId(context);
        durableSubscriptions.forEach(context::unsubscribe);
      } catch (JMSRuntimeException ignored) {
        remember("cleanup-failed", JmsDestination.topic("durable-subscription"), null);
      }
    }
    durableSubscriptions.clear();
    published.clear();
    destinationLocks.clear();
    buffered.clear();
    evidence.clear();
    connectionFactory = null;
  }

  private MessageRecord fromNative(JmsDestination destination, Message message)
      throws JMSException {
    MessageRecord local = published.remove(message.getJMSMessageID());
    if (local != null) return local;
    if (!(message instanceof BytesMessage bytes))
      throw new IllegalArgumentException("Only JMS BytesMessage payloads are supported");
    long bodyLength = bytes.getBodyLength();
    if (bodyLength > Integer.MAX_VALUE)
      throw new IllegalArgumentException("JMS payload is too large");
    byte[] payload = new byte[Math.toIntExact(bodyLength)];
    bytes.readBytes(payload);
    Map<String, String> headers = new LinkedHashMap<>();
    Enumeration<?> names = message.getPropertyNames();
    while (names.hasMoreElements()) {
      String name = String.valueOf(names.nextElement());
      if (name.startsWith(HEADER_PREFIX))
        headers.put(decodeHeaderName(name), message.getStringProperty(name));
    }
    String correlationName = message.getStringProperty(CORRELATION_NAME);
    Optional<Correlation> correlation =
        correlationName == null || message.getJMSCorrelationID() == null
            ? Optional.empty()
            : Optional.of(new Correlation(correlationName, message.getJMSCorrelationID()));
    return record(
        destination,
        new MessageEnvelope(payload, headers, correlation),
        message,
        message.getJMSRedelivered());
  }

  private static MessageRecord record(
      JmsDestination destination, MessageEnvelope envelope, Message message, boolean redelivered)
      throws JMSException {
    return new MessageRecord(
        envelope,
        Instant.now(),
        Map.of(
            "destinationType",
            destination.type().name(),
            "destination",
            destination.name(),
            "messageId",
            safe(message.getJMSMessageID()),
            "redelivered",
            redelivered));
  }

  private static Destination resolve(JMSContext context, JmsDestination destination) {
    return switch (destination.type()) {
      case QUEUE -> context.createQueue(destination.name());
      case TOPIC -> context.createTopic(destination.name());
    };
  }

  private MessageRecord removeMatch(
      JmsDestination destination, String durableName, MessageQuery query) {
    Deque<MessageRecord> records =
        buffered.computeIfAbsent(
            destinationKey(destination, durableName), ignored -> new ArrayDeque<>());
    Iterator<MessageRecord> iterator = records.iterator();
    while (iterator.hasNext()) {
      MessageRecord record = iterator.next();
      if (query.selector().matches(record)) {
        iterator.remove();
        return record;
      }
    }
    return null;
  }

  private static String destinationKey(JmsDestination destination, String durableName) {
    return destination.type() + ":" + destination.name() + ":" + safe(durableName);
  }

  private void configureClientId(JMSContext context) {
    if (settings.getClientId() != null && !settings.getClientId().isBlank())
      context.setClientID(settings.getClientId());
  }

  private void remember(String operation, JmsDestination destination, String subscription) {
    while (evidence.size() >= settings.getMaximumEvidenceRecords()) evidence.pollFirst();
    evidence.addLast(
        operation
            + " type="
            + destination.type()
            + " destination="
            + destination.name()
            + (subscription == null ? "" : " subscription=" + subscription));
  }

  private void ensureReady() {
    if (state.get() != ControllerState.READY)
      throw new IllegalStateException("JMS controller is not READY: " + state.get());
  }

  private JmsControllerException failure(String operation, String correction, Throwable cause) {
    return new JmsControllerException(operation, correction, cause);
  }

  private static String normalize(String selector) {
    return selector == null || selector.isBlank() ? null : selector;
  }

  private static Duration elapsed(long started) {
    return Duration.ofNanos(System.nanoTime() - started);
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }

  private static String encodeHeaderName(String value) {
    if (value.matches("[A-Za-z_$][A-Za-z0-9_$]*")) return value;
    return HEADER_PREFIX
        + Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static String decodeHeaderName(String value) {
    return new String(
        Base64.getUrlDecoder().decode(value.substring(HEADER_PREFIX.length())),
        java.nio.charset.StandardCharsets.UTF_8);
  }

  private static InterruptedException interrupted() {
    Thread.currentThread().interrupt();
    return new InterruptedException("JMS consumption interrupted");
  }
}

package com.codinglair.taf.messaging.rabbitmq;

import com.codinglair.taf.messaging.*;
import com.codinglair.taf.runtime.core.controller.*;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import com.codinglair.taf.runtime.secret.SecretManager;
import com.codinglair.taf.runtime.secret.SecretRequestContext;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.Connection;

final class DefaultRabbitController implements RabbitController {
  private static final String CORRELATION_NAME = "taf-correlation-name";
  private static final String MESSAGE_ID = "taf-message-id";
  private static final Set<String> RESERVED = Set.of(CORRELATION_NAME, MESSAGE_ID);
  private final ControllerIdentity identity;
  private final RabbitControllerSettings settings;
  private final SecretManager secrets;
  private final AtomicReference<ControllerState> state = new AtomicReference<>(ControllerState.NEW);
  private final Map<String, Object> queueLocks = new ConcurrentHashMap<>();
  private final Map<String, Deque<MessageRecord>> buffered = new ConcurrentHashMap<>();
  private final Map<String, MessageRecord> published = new ConcurrentHashMap<>();
  private final Deque<String> evidence = new ConcurrentLinkedDeque<>();
  private volatile CachingConnectionFactory connectionFactory;

  DefaultRabbitController(String name, RabbitControllerSettings settings, SecretManager secrets) {
    identity = new ControllerIdentity(RabbitController.class, name);
    this.settings = Objects.requireNonNull(settings, "settings");
    this.secrets = Objects.requireNonNull(secrets, "secrets");
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
      settings.validate("taf.messaging.rabbitmq.controllers." + identity.name());
      CachingConnectionFactory factory = new CachingConnectionFactory();
      factory.setAddresses(settings.getAddresses());
      factory.setUsername(settings.getUsername());
      SecretRequestContext requestContext =
          new SecretRequestContext(
              "taf-messaging-rabbitmq", context.sessionId(), "configured", true);
      try (var password = secrets.resolve(settings.getPasswordReference(), requestContext)) {
        factory.setPassword(password.useAsString());
      }
      factory.setVirtualHost(settings.getVirtualHost());
      factory.setConnectionTimeout(Math.toIntExact(settings.getOperationTimeout().toMillis()));
      factory.createConnection().close();
      connectionFactory = factory;
      state.set(ControllerState.READY);
    } catch (RuntimeException failure) {
      state.set(ControllerState.FAILED);
      destroyFactory();
      throw failure(
          "initialize", "verify broker address, credential reference, and readiness", failure);
    }
  }

  @Override
  public HealthResult health() {
    if (state.get() != ControllerState.READY)
      return new HealthResult(
          HealthResult.Status.UNAVAILABLE,
          "RabbitMQ controller is not ready",
          Map.of("state", state.get().name()));
    try (Connection connection = connectionFactory.createConnection()) {
      return new HealthResult(
          HealthResult.Status.HEALTHY,
          "RabbitMQ broker is reachable",
          Map.of("localPort", Integer.toString(connection.getLocalPort())));
    } catch (RuntimeException failure) {
      return new HealthResult(
          HealthResult.Status.UNAVAILABLE,
          "RabbitMQ broker is unavailable",
          Map.of("cause", failure.getClass().getSimpleName()));
    }
  }

  @Override
  public void declareTopology(RabbitTopology topology) {
    ensureReady();
    Objects.requireNonNull(topology, "topology");
    withChannel(
        "declare topology",
        channel -> {
          channel.exchangeDeclare(topology.exchange(), topology.exchangeType(), topology.durable());
          channel.queueDeclare(
              topology.queue(), topology.durable(), false, !topology.durable(), Map.of());
          channel.queueBind(topology.queue(), topology.exchange(), topology.routingKey());
          return null;
        });
    remember("topology", topology.queue(), topology.exchange(), topology.routingKey(), "declared");
  }

  @Override
  public MessageRecord publish(String destination, MessageEnvelope message) {
    requireDestination(destination);
    ensureQueue(destination);
    return publishNative("", destination, destination, message);
  }

  @Override
  public MessageRecord publish(RabbitTopology topology, MessageEnvelope message) {
    declareTopology(topology);
    return publishNative(topology.exchange(), topology.routingKey(), topology.queue(), message);
  }

  @Override
  public ConsumptionResult consume(MessageQuery query) throws InterruptedException {
    return consume(query, RabbitAcknowledgment.ACK);
  }

  @Override
  public ConsumptionResult consume(MessageQuery query, RabbitAcknowledgment acknowledgment)
      throws InterruptedException {
    ensureReady();
    Objects.requireNonNull(query, "query");
    Objects.requireNonNull(acknowledgment, "acknowledgment");
    requireDestination(query.destination());
    long started = System.nanoTime();
    long deadline = started + query.timeout().toNanos();
    synchronized (queueLocks.computeIfAbsent(query.destination(), ignored -> new Object())) {
      MessageRecord cached = removeMatch(query);
      if (cached != null) return ConsumptionResult.matched(cached, elapsed(started));
      ensureQueue(query.destination());
      try (Connection connection = connectionFactory.createConnection();
          Channel channel = connection.createChannel(false)) {
        while (System.nanoTime() < deadline) {
          if (Thread.currentThread().isInterrupted())
            throw new InterruptedException("RabbitMQ consumption interrupted");
          GetResponse delivery = channel.basicGet(query.destination(), false);
          if (delivery == null) {
            Thread.sleep(
                Math.min(
                    10, Math.max(1, Duration.ofNanos(deadline - System.nanoTime()).toMillis())));
            continue;
          }
          MessageRecord candidate = fromNative(query.destination(), delivery);
          if (query.selector().matches(candidate)) {
            dispose(channel, delivery.getEnvelope().getDeliveryTag(), acknowledgment);
            remember(
                "consume",
                query.destination(),
                delivery.getEnvelope().getExchange(),
                delivery.getEnvelope().getRoutingKey(),
                acknowledgment.name());
            return ConsumptionResult.matched(candidate, elapsed(started));
          }
          channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
          buffer(query.destination(), candidate);
        }
        return ConsumptionResult.noMatch(elapsed(started));
      } catch (InterruptedException failure) {
        Thread.currentThread().interrupt();
        throw failure;
      } catch (IOException | TimeoutException | RuntimeException failure) {
        throw failure("consume", "verify queue, broker availability, and timeout", failure);
      }
    }
  }

  @Override
  public Stream<TestArtifact> collectArtifacts(ArtifactReason reason) {
    synchronized (evidence) {
      if (evidence.isEmpty()) return Stream.empty();
      return Stream.of(
          TestArtifact.of(
              "rabbitmq-metadata.txt",
              "messaging-diagnostic",
              String.join(System.lineSeparator(), evidence),
              "text/plain"));
    }
  }

  @Override
  public void close() {
    if (state.getAndSet(ControllerState.CLOSED) == ControllerState.CLOSED) return;
    destroyFactory();
    queueLocks.clear();
    buffered.clear();
    published.clear();
    evidence.clear();
  }

  private MessageRecord publishNative(
      String exchange, String routingKey, String queue, MessageEnvelope message) {
    ensureReady();
    Objects.requireNonNull(message, "message");
    rejectReservedHeaders(message.headers());
    String messageId = UUID.randomUUID().toString();
    Map<String, Object> headers = new LinkedHashMap<>(message.headers());
    headers.put(MESSAGE_ID, messageId);
    message.correlation().ifPresent(value -> headers.put(CORRELATION_NAME, value.name()));
    AMQP.BasicProperties properties =
        new AMQP.BasicProperties.Builder()
            .messageId(messageId)
            .correlationId(message.correlation().map(Correlation::value).orElse(null))
            .headers(headers)
            .timestamp(Date.from(Instant.now()))
            .build();
    withChannel(
        "publish",
        channel -> {
          channel.basicPublish(exchange, routingKey, properties, message.payload());
          return null;
        });
    MessageRecord result = record(message, queue, exchange, routingKey, messageId, false);
    published.put(messageId, result);
    remember("publish", queue, exchange, routingKey, "sent");
    return result;
  }

  private MessageRecord fromNative(String queue, GetResponse delivery) {
    String messageId = delivery.getProps().getMessageId();
    MessageRecord local = messageId == null ? null : published.remove(messageId);
    if (local != null) return local;
    Map<String, String> headers = new LinkedHashMap<>();
    Map<String, Object> nativeHeaders = delivery.getProps().getHeaders();
    if (nativeHeaders != null)
      nativeHeaders.forEach(
          (name, value) -> {
            if (!RESERVED.contains(name)) headers.put(name, String.valueOf(value));
          });
    Object correlationName = nativeHeaders == null ? null : nativeHeaders.get(CORRELATION_NAME);
    Optional<Correlation> correlation =
        correlationName == null || delivery.getProps().getCorrelationId() == null
            ? Optional.empty()
            : Optional.of(
                new Correlation(
                    String.valueOf(correlationName), delivery.getProps().getCorrelationId()));
    MessageEnvelope envelope = new MessageEnvelope(delivery.getBody(), headers, correlation);
    return record(
        envelope,
        queue,
        delivery.getEnvelope().getExchange(),
        delivery.getEnvelope().getRoutingKey(),
        messageId,
        delivery.getEnvelope().isRedeliver());
  }

  private static MessageRecord record(
      MessageEnvelope message,
      String queue,
      String exchange,
      String routingKey,
      String messageId,
      boolean redelivered) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("queue", queue);
    metadata.put("exchange", exchange);
    metadata.put("routingKey", routingKey);
    metadata.put("messageId", messageId == null ? "" : messageId);
    metadata.put("redelivered", redelivered);
    return new MessageRecord(message, Instant.now(), metadata);
  }

  private void ensureQueue(String queue) {
    withChannel(
        "declare queue",
        channel -> {
          channel.queueDeclare(queue, false, false, true, Map.of());
          return null;
        });
  }

  private void dispose(Channel channel, long tag, RabbitAcknowledgment acknowledgment)
      throws IOException {
    switch (acknowledgment) {
      case ACK -> channel.basicAck(tag, false);
      case NACK_REQUEUE -> channel.basicNack(tag, false, true);
      case NACK_DISCARD -> channel.basicNack(tag, false, false);
    }
  }

  private MessageRecord removeMatch(MessageQuery query) {
    Deque<MessageRecord> queue =
        buffered.computeIfAbsent(query.destination(), ignored -> new ArrayDeque<>());
    Iterator<MessageRecord> iterator = queue.iterator();
    while (iterator.hasNext()) {
      MessageRecord value = iterator.next();
      if (query.selector().matches(value)) {
        iterator.remove();
        return value;
      }
    }
    return null;
  }

  private void buffer(String queueName, MessageRecord record) {
    Deque<MessageRecord> queue = buffered.computeIfAbsent(queueName, ignored -> new ArrayDeque<>());
    if (queue.size() >= settings.getMaximumBufferedRecords())
      throw failure(
          "buffer unmatched record",
          "narrow the selector or increase maximum-buffered-records",
          null);
    queue.addLast(record);
  }

  void remember(
      String operation, String queue, String exchange, String routingKey, String disposition) {
    String entry =
        operation
            + " queue="
            + queue
            + " exchange="
            + exchange
            + " routingKey="
            + routingKey
            + " disposition="
            + disposition;
    synchronized (evidence) {
      while (evidence.size() >= settings.getMaximumBufferedRecords()) evidence.removeFirst();
      evidence.addLast(entry);
    }
  }

  private <T> T withChannel(String operation, ChannelAction<T> action) {
    ensureReady();
    try (Connection connection = connectionFactory.createConnection();
        Channel channel = connection.createChannel(false)) {
      return action.execute(channel);
    } catch (IOException | TimeoutException | RuntimeException failure) {
      throw failure(operation, "verify topology, authorization, and broker availability", failure);
    }
  }

  private void destroyFactory() {
    CachingConnectionFactory current = connectionFactory;
    connectionFactory = null;
    if (current != null) current.destroy();
  }

  private void ensureReady() {
    if (state.get() != ControllerState.READY)
      throw new IllegalStateException("RabbitMQ controller is not READY: " + state.get());
  }

  private static void requireDestination(String value) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("destination must not be blank");
  }

  private static void rejectReservedHeaders(Map<String, String> headers) {
    if (headers.keySet().stream().anyMatch(RESERVED::contains))
      throw new IllegalArgumentException("Message headers use a reserved TAF RabbitMQ header");
  }

  private RabbitControllerException failure(String operation, String correction, Throwable cause) {
    return new RabbitControllerException(operation, correction, cause);
  }

  private static Duration elapsed(long started) {
    return Duration.ofNanos(System.nanoTime() - started);
  }

  @FunctionalInterface
  private interface ChannelAction<T> {
    T execute(Channel channel) throws IOException;
  }
}

package com.codinglair.taf.messaging.kafka;

import com.codinglair.taf.messaging.*;
import com.codinglair.taf.runtime.core.controller.*;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;

final class DefaultKafkaController implements KafkaController {
  private static final String CORRELATION_NAME = "taf-correlation-name";
  private static final String CORRELATION_VALUE = "taf-correlation-value";
  private static final Set<String> RESERVED = Set.of(CORRELATION_NAME, CORRELATION_VALUE);
  private final ControllerIdentity identity;
  private final KafkaControllerSettings settings;
  private final AtomicReference<ControllerState> state = new AtomicReference<>(ControllerState.NEW);
  private final ConcurrentMap<String, Object> topicLocks = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Deque<MessageRecord>> buffered = new ConcurrentHashMap<>();
  private final ConcurrentMap<NativeRecordId, MessageRecord> published = new ConcurrentHashMap<>();
  private final Deque<String> evidence = new ConcurrentLinkedDeque<>();
  private volatile Admin admin;
  private volatile Producer<byte[], byte[]> producer;

  DefaultKafkaController(String name, KafkaControllerSettings settings) {
    identity = new ControllerIdentity(KafkaController.class, name);
    this.settings = Objects.requireNonNull(settings, "settings");
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
      settings.validate("taf.messaging.kafka.controllers." + identity.name());
      admin = Admin.create(commonProperties("admin"));
      admin
          .describeCluster()
          .clusterId()
          .get(settings.getOperationTimeout().toMillis(), TimeUnit.MILLISECONDS);
      producer =
          new KafkaProducer<>(
              commonProperties("producer"), new ByteArraySerializer(), new ByteArraySerializer());
      state.set(ControllerState.READY);
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      state.set(ControllerState.FAILED);
      closeClients();
      throw new KafkaControllerException(
          "initialize", "verify broker availability and preserve cancellation", failure);
    } catch (RuntimeException | ExecutionException | TimeoutException failure) {
      state.set(ControllerState.FAILED);
      closeClients();
      throw new KafkaControllerException(
          "initialize", "verify bootstrap servers, credentials, and broker readiness", failure);
    }
  }

  @Override
  public HealthResult health() {
    if (state.get() != ControllerState.READY)
      return new HealthResult(
          HealthResult.Status.UNAVAILABLE,
          "Kafka controller is not ready",
          Map.of("state", state.get().name()));
    try {
      String clusterId =
          admin
              .describeCluster()
              .clusterId()
              .get(settings.getOperationTimeout().toMillis(), TimeUnit.MILLISECONDS);
      return new HealthResult(
          HealthResult.Status.HEALTHY, "Kafka broker is reachable", Map.of("clusterId", clusterId));
    } catch (Exception failure) {
      if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
      return new HealthResult(
          HealthResult.Status.UNAVAILABLE,
          "Kafka broker is unavailable",
          Map.of("cause", failure.getClass().getSimpleName()));
    }
  }

  @Override
  public MessageRecord publish(String destination, MessageEnvelope message) {
    ensureReady();
    requireDestination(destination);
    Objects.requireNonNull(message, "message");
    rejectReservedHeaders(message.headers());
    ensureTopic(destination);
    Instant observedAt = Instant.ofEpochMilli(System.currentTimeMillis());
    byte[] key =
        message
            .correlation()
            .map(Correlation::value)
            .map(value -> value.getBytes(StandardCharsets.UTF_8))
            .orElse(null);
    ProducerRecord<byte[], byte[]> outgoing =
        new ProducerRecord<>(
            destination, null, observedAt.toEpochMilli(), key, message.payload(), headers(message));
    try {
      RecordMetadata metadata =
          producer
              .send(outgoing)
              .get(settings.getOperationTimeout().toMillis(), TimeUnit.MILLISECONDS);
      MessageRecord result =
          record(
              message,
              observedAt,
              metadata.topic(),
              metadata.partition(),
              metadata.offset(),
              key,
              settings.getGroupId());
      published.put(
          new NativeRecordId(metadata.topic(), metadata.partition(), metadata.offset()), result);
      remember("publish", result);
      return result;
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new KafkaControllerException(
          "publish", "preserve cancellation and verify broker availability", failure);
    } catch (ExecutionException | TimeoutException failure) {
      throw new KafkaControllerException(
          "publish", "verify topic policy and broker availability", failure);
    }
  }

  @Override
  public ConsumptionResult consume(MessageQuery query) throws InterruptedException {
    return consume(query, settings.getGroupId());
  }

  @Override
  public ConsumptionResult consume(MessageQuery query, String consumerGroup)
      throws InterruptedException {
    ensureReady();
    Objects.requireNonNull(query, "query");
    requireDestination(query.destination());
    if (consumerGroup == null || consumerGroup.isBlank())
      throw new IllegalArgumentException("consumerGroup must not be blank");
    long started = System.nanoTime();
    long deadline = started + query.timeout().toNanos();
    synchronized (topicLocks.computeIfAbsent(query.destination(), ignored -> new Object())) {
      MessageRecord cached = removeMatch(query);
      if (cached != null) return ConsumptionResult.matched(cached, elapsed(started));
      ensureTopic(query.destination());
      try (Consumer<byte[], byte[]> consumer = consumer(consumerGroup)) {
        consumer.subscribe(List.of(query.destination()));
        while (System.nanoTime() < deadline) {
          if (Thread.currentThread().isInterrupted())
            throw new InterruptedException("Kafka consumption interrupted");
          Duration remaining = Duration.ofNanos(Math.max(1, deadline - System.nanoTime()));
          ConsumerRecords<byte[], byte[]> records =
              consumer.poll(min(remaining, Duration.ofMillis(100)));
          for (ConsumerRecord<byte[], byte[]> nativeRecord : records) {
            MessageRecord candidate = fromNative(nativeRecord, consumerGroup);
            if (query.selector().matches(candidate)) {
              commitAfter(consumer, nativeRecord);
              remember("consume", candidate);
              return ConsumptionResult.matched(candidate, elapsed(started));
            }
            buffer(query.destination(), candidate);
            commitAfter(consumer, nativeRecord);
          }
        }
        return ConsumptionResult.noMatch(elapsed(started));
      } catch (InterruptException failure) {
        Thread.currentThread().interrupt();
        InterruptedException interrupted =
            new InterruptedException("Kafka consumption interrupted");
        interrupted.initCause(failure);
        throw interrupted;
      } catch (KafkaControllerException failure) {
        throw failure;
      } catch (RuntimeException failure) {
        throw new KafkaControllerException(
            "consume", "verify group, topic, broker availability, and timeout", failure);
      }
    }
  }

  @Override
  public Stream<TestArtifact> collectArtifacts(ArtifactReason reason) {
    synchronized (evidence) {
      if (evidence.isEmpty()) return Stream.empty();
      return Stream.of(
          TestArtifact.of(
              "kafka-metadata.txt",
              "messaging-diagnostic",
              String.join(System.lineSeparator(), evidence),
              "text/plain"));
    }
  }

  @Override
  public void close() {
    ControllerState previous = state.getAndSet(ControllerState.CLOSED);
    if (previous == ControllerState.CLOSED) return;
    closeClients();
    buffered.clear();
    published.clear();
    topicLocks.clear();
    evidence.clear();
  }

  private Properties commonProperties(String role) {
    Properties properties = new Properties();
    properties.put("bootstrap.servers", String.join(",", settings.getBootstrapServers()));
    properties.put("client.id", settings.getClientIdPrefix() + "-" + identity.name() + "-" + role);
    properties.put("request.timeout.ms", Long.toString(settings.getOperationTimeout().toMillis()));
    properties.put(
        "default.api.timeout.ms", Long.toString(settings.getOperationTimeout().toMillis()));
    return properties;
  }

  private Consumer<byte[], byte[]> consumer(String group) {
    Properties properties = commonProperties("consumer");
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, group);
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    return new KafkaConsumer<>(
        properties, new ByteArrayDeserializer(), new ByteArrayDeserializer());
  }

  private void ensureTopic(String topic) {
    try {
      if (admin
          .describeTopics(List.of(topic))
          .allTopicNames()
          .get(settings.getOperationTimeout().toMillis(), TimeUnit.MILLISECONDS)
          .containsKey(topic)) return;
    } catch (ExecutionException failure) {
      if (settings.getTopicPolicy() == KafkaTopicPolicy.REQUIRE_EXISTING)
        throw new KafkaControllerException(
            "resolve topic", "create the topic or select CREATE_IF_MISSING", failure);
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new KafkaControllerException(
          "resolve topic", "preserve cancellation and retry when appropriate", failure);
    } catch (TimeoutException failure) {
      throw new KafkaControllerException("resolve topic", "verify broker availability", failure);
    }
    if (settings.getTopicPolicy() == KafkaTopicPolicy.REQUIRE_EXISTING)
      throw new KafkaControllerException(
          "resolve topic", "create the topic or select CREATE_IF_MISSING", null);
    try {
      admin
          .createTopics(
              List.of(
                  new NewTopic(
                      topic, settings.getTopicPartitions(), settings.getTopicReplicationFactor())))
          .all()
          .get(settings.getOperationTimeout().toMillis(), TimeUnit.MILLISECONDS);
    } catch (ExecutionException failure) {
      if (!(failure.getCause() instanceof TopicExistsException))
        throw new KafkaControllerException(
            "create topic", "verify broker authorization and replication settings", failure);
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new KafkaControllerException(
          "create topic", "preserve cancellation and retry when appropriate", failure);
    } catch (TimeoutException failure) {
      throw new KafkaControllerException("create topic", "verify broker availability", failure);
    }
  }

  private MessageRecord fromNative(ConsumerRecord<byte[], byte[]> value, String group) {
    MessageRecord local =
        published.remove(new NativeRecordId(value.topic(), value.partition(), value.offset()));
    if (local != null && Objects.equals(local.nativeMetadata().get("consumerGroup"), group))
      return local;
    Map<String, String> headers = new LinkedHashMap<>();
    for (Header header : value.headers())
      if (!RESERVED.contains(header.key()))
        headers.put(header.key(), new String(header.value(), StandardCharsets.UTF_8));
    String correlationName = header(value, CORRELATION_NAME);
    String correlationValue = header(value, CORRELATION_VALUE);
    Optional<Correlation> correlation =
        correlationName == null || correlationValue == null
            ? Optional.empty()
            : Optional.of(new Correlation(correlationName, correlationValue));
    MessageEnvelope envelope = new MessageEnvelope(value.value(), headers, correlation);
    return record(
        envelope,
        Instant.ofEpochMilli(value.timestamp()),
        value.topic(),
        value.partition(),
        value.offset(),
        value.key(),
        group);
  }

  private static Iterable<Header> headers(MessageEnvelope envelope) {
    List<Header> result = new ArrayList<>();
    envelope
        .headers()
        .forEach(
            (name, value) ->
                result.add(new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8))));
    envelope
        .correlation()
        .ifPresent(
            value -> {
              result.add(
                  new RecordHeader(
                      CORRELATION_NAME, value.name().getBytes(StandardCharsets.UTF_8)));
              result.add(
                  new RecordHeader(
                      CORRELATION_VALUE, value.value().getBytes(StandardCharsets.UTF_8)));
            });
    return result;
  }

  private static String header(ConsumerRecord<byte[], byte[]> record, String name) {
    Header header = record.headers().lastHeader(name);
    return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
  }

  private static MessageRecord record(
      MessageEnvelope envelope,
      Instant observedAt,
      String topic,
      int partition,
      long offset,
      byte[] key,
      String group) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("topic", topic);
    metadata.put("partition", partition);
    metadata.put("offset", offset);
    metadata.put("key", key == null ? "" : Base64.getEncoder().encodeToString(key));
    metadata.put("consumerGroup", group);
    return new MessageRecord(envelope, observedAt, metadata);
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

  private void buffer(String topic, MessageRecord record) {
    Deque<MessageRecord> queue = buffered.computeIfAbsent(topic, ignored -> new ArrayDeque<>());
    if (queue.size() >= settings.getMaximumBufferedRecords())
      throw new KafkaControllerException(
          "buffer unmatched record",
          "narrow the selector or increase maximum-buffered-records",
          null);
    queue.addLast(record);
  }

  private static void commitAfter(
      Consumer<byte[], byte[]> consumer, ConsumerRecord<byte[], byte[]> record) {
    consumer.commitSync(
        Map.of(
            new TopicPartition(record.topic(), record.partition()),
            new OffsetAndMetadata(record.offset() + 1)));
  }

  void remember(String operation, MessageRecord record) {
    String entry =
        operation
            + " topic="
            + record.nativeMetadata().get("topic")
            + " partition="
            + record.nativeMetadata().get("partition")
            + " offset="
            + record.nativeMetadata().get("offset")
            + " group="
            + record.nativeMetadata().get("consumerGroup");
    synchronized (evidence) {
      while (evidence.size() >= settings.getMaximumBufferedRecords()) evidence.removeFirst();
      evidence.addLast(entry);
    }
  }

  private void closeClients() {
    Producer<byte[], byte[]> currentProducer = producer;
    Admin currentAdmin = admin;
    producer = null;
    admin = null;
    if (currentProducer != null) currentProducer.close(settings.getOperationTimeout());
    if (currentAdmin != null) currentAdmin.close(settings.getOperationTimeout());
  }

  private void ensureReady() {
    if (state.get() != ControllerState.READY)
      throw new IllegalStateException("Kafka controller is not READY: " + state.get());
  }

  private static void requireDestination(String value) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("destination must not be blank");
  }

  private static void rejectReservedHeaders(Map<String, String> headers) {
    if (headers.keySet().stream().anyMatch(RESERVED::contains))
      throw new IllegalArgumentException("Message headers use a reserved TAF Kafka header");
  }

  private static Duration elapsed(long started) {
    return Duration.ofNanos(System.nanoTime() - started);
  }

  private static Duration min(Duration first, Duration second) {
    return first.compareTo(second) <= 0 ? first : second;
  }

  private record NativeRecordId(String topic, int partition, long offset) {}
}

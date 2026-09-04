package com.codinglair.taf.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.messaging.*;
import com.codinglair.taf.messaging.contract.MessagingControllerContract;
import com.codinglair.taf.messaging.contract.MessagingControllerContractHarness;
import com.codinglair.taf.runtime.core.controller.*;
import com.codinglair.taf.runtime.core.reporting.ArtifactCollector;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import com.codinglair.taf.runtime.environment.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.DockerClientFactory;

@EnabledIfSystemProperty(named = "taf.containers.enabled", matches = "true")
@DisplayName("RabbitMQ Testcontainers messaging contract")
class RabbitContainerContractTest extends MessagingControllerContract {
  private static ContainerLifecycleCoordinator coordinator;
  private static RabbitEnvironmentProvider provider;
  private static EnvironmentResource broker;

  @BeforeAll
  static void startBroker() {
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is required for the RabbitMQ container contract");
    coordinator = new ContainerLifecycleCoordinator();
    provider = new RabbitEnvironmentProvider(coordinator);
    EnvironmentRequest request =
        new EnvironmentRequest(
            "msg003",
            RabbitEnvironmentProvider.TYPE,
            EnvironmentMode.CONTAINER,
            Set.of(),
            Map.of(ContainerLifecycle.PROPERTY, "isolated"),
            Duration.ofMinutes(2));
    assertThat(provider.preflight(request).status()).isEqualTo(EnvironmentStatus.READY);
    broker = provider.provision(request);
  }

  @AfterAll
  static void stopBroker() {
    if (provider != null) provider.cleanup();
    if (coordinator != null) coordinator.close();
    if (broker != null)
      assertThat(broker.diagnose().status()).isEqualTo(EnvironmentStatus.UNAVAILABLE);
  }

  @Override
  protected MessagingControllerContractHarness createHarness() {
    return new Harness(broker.properties().get("taf.messaging.rabbitmq.addresses"));
  }

  @Nested
  @DisplayName("RabbitMQ native semantics")
  class NativeSemantics {
    @Test
    @DisplayName("routes only matching keys to each isolated queue")
    void routingMatrix() throws Exception {
      DefaultRabbitController controller = controller("routing");
      String suffix = UUID.randomUUID().toString();
      RabbitTopology created =
          new RabbitTopology("orders-" + suffix, "created-" + suffix, "created");
      RabbitTopology cancelled =
          new RabbitTopology("orders-" + suffix, "cancelled-" + suffix, "cancelled");
      try {
        controller.declareTopology(created);
        controller.declareTopology(cancelled);
        controller.publish(created, new MessageEnvelope(new byte[] {1}));
        controller.publish(cancelled, new MessageEnvelope(new byte[] {2}));
        assertThat(
                controller
                    .consume(query(created.queue()))
                    .record()
                    .orElseThrow()
                    .envelope()
                    .payload())
            .containsExactly(1);
        assertThat(
                controller
                    .consume(query(cancelled.queue()))
                    .record()
                    .orElseThrow()
                    .envelope()
                    .payload())
            .containsExactly(2);
      } finally {
        controller.close();
      }
    }

    @Test
    @DisplayName("nack with requeue redelivers while nack discard removes the delivery")
    void nackAndRequeue() throws Exception {
      DefaultRabbitController controller = controller("nack");
      String queue = "nack-" + UUID.randomUUID();
      try {
        controller.publish(queue, new MessageEnvelope(new byte[] {7}));
        ConsumptionResult nacked =
            controller.consume(query(queue), RabbitAcknowledgment.NACK_REQUEUE);
        ConsumptionResult redelivered =
            controller.consume(query(queue), RabbitAcknowledgment.NACK_DISCARD);
        ConsumptionResult discarded =
            controller.consume(
                new MessageQuery(queue, MessageSelector.any(), Duration.ofMillis(150)));
        assertThat(nacked.status()).isEqualTo(ConsumptionResult.Status.MATCHED);
        assertThat(redelivered.record().orElseThrow().nativeMetadata())
            .containsEntry("redelivered", true);
        assertThat(discarded.status()).isEqualTo(ConsumptionResult.Status.NO_MATCH);
      } finally {
        controller.close();
      }
    }

    @Test
    @DisplayName("parallel isolated queues never consume each other's messages")
    void parallelQueueIsolation() throws Exception {
      DefaultRabbitController controller = controller("parallel");
      String firstQueue = "parallel-a-" + UUID.randomUUID();
      String secondQueue = "parallel-b-" + UUID.randomUUID();
      try {
        controller.publish(firstQueue, new MessageEnvelope(new byte[] {1}));
        controller.publish(secondQueue, new MessageEnvelope(new byte[] {2}));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
          var first = executor.submit(() -> controller.consume(query(firstQueue)));
          var second = executor.submit(() -> controller.consume(query(secondQueue)));
          assertThat(first.get().record().orElseThrow().envelope().payload()).containsExactly(1);
          assertThat(second.get().record().orElseThrow().envelope().payload()).containsExactly(2);
        }
      } finally {
        controller.close();
      }
    }
  }

  private static MessageQuery query(String queue) {
    return new MessageQuery(queue, MessageSelector.any(), Duration.ofSeconds(5));
  }

  private static DefaultRabbitController controller(String name) {
    RabbitControllerSettings settings = new RabbitControllerSettings();
    settings.setAddresses(broker.properties().get("taf.messaging.rabbitmq.addresses"));
    DefaultRabbitController controller =
        new DefaultRabbitController(name, settings, TestSecretManager.GUEST);
    controller.initialize(context(name));
    return controller;
  }

  private static ControllerContext context(String name) {
    return new ControllerContext(
        "session-" + name,
        EnvironmentAccess.unavailable(),
        new ArtifactCollector(
            TafTest.of(name, RabbitContainerContractTest.class.getName()),
            "session-" + name,
            name));
  }

  private static final class Harness implements MessagingControllerContractHarness {
    private final CountDownLatch consumeStarted = new CountDownLatch(1);
    private final DefaultRabbitController delegate;
    private final RabbitController controller;
    private final String prefix = "contract-" + UUID.randomUUID() + "-";

    private Harness(String addresses) {
      RabbitControllerSettings settings = new RabbitControllerSettings();
      settings.setAddresses(addresses);
      delegate = new DefaultRabbitController("contract", settings, TestSecretManager.GUEST);
      delegate.initialize(context("contract"));
      controller =
          new RabbitController() {
            @Override
            public ConsumptionResult consume(MessageQuery query) throws InterruptedException {
              consumeStarted.countDown();
              return delegate.consume(unique(query));
            }

            @Override
            public ConsumptionResult consume(
                MessageQuery query, RabbitAcknowledgment acknowledgment)
                throws InterruptedException {
              consumeStarted.countDown();
              return delegate.consume(unique(query), acknowledgment);
            }

            @Override
            public MessageRecord publish(String destination, MessageEnvelope message) {
              return delegate.publish(unique(destination), message);
            }

            @Override
            public MessageRecord publish(RabbitTopology topology, MessageEnvelope message) {
              return delegate.publish(unique(topology), message);
            }

            @Override
            public void declareTopology(RabbitTopology topology) {
              delegate.declareTopology(unique(topology));
            }

            @Override
            public ControllerIdentity identity() {
              return delegate.identity();
            }

            @Override
            public ControllerState state() {
              return delegate.state();
            }

            @Override
            public void initialize(ControllerContext context) {
              delegate.initialize(context);
            }

            @Override
            public HealthResult health() {
              return delegate.health();
            }

            @Override
            public java.util.stream.Stream<
                    com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact>
                collectArtifacts(ArtifactReason reason) {
              return delegate.collectArtifacts(reason);
            }

            @Override
            public void close() {
              delegate.close();
            }
          };
    }

    @Override
    public RabbitController controller() {
      return controller;
    }

    @Override
    public void awaitConsumeStarted(Duration timeout) throws InterruptedException {
      assertThat(consumeStarted.await(timeout.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
    }

    @Override
    public boolean isClosed() {
      return delegate.state() == ControllerState.CLOSED;
    }

    @Override
    public void close() {
      delegate.close();
    }

    private String unique(String destination) {
      return prefix + destination;
    }

    private MessageQuery unique(MessageQuery query) {
      return new MessageQuery(unique(query.destination()), query.selector(), query.timeout());
    }

    private RabbitTopology unique(RabbitTopology topology) {
      return new RabbitTopology(
          unique(topology.exchange()),
          unique(topology.queue()),
          topology.routingKey(),
          topology.exchangeType(),
          topology.durable());
    }
  }
}

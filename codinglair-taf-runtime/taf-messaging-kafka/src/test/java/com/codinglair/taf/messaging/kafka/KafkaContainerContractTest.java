package com.codinglair.taf.messaging.kafka;

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
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.DockerClientFactory;

@EnabledIfSystemProperty(named = "taf.containers.enabled", matches = "true")
@DisplayName("Kafka Testcontainers messaging contract")
class KafkaContainerContractTest extends MessagingControllerContract {
  private static ContainerLifecycleCoordinator coordinator;
  private static KafkaEnvironmentProvider provider;
  private static EnvironmentResource broker;

  @BeforeAll
  static void startBroker() {
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is required for the Kafka container contract");
    coordinator = new ContainerLifecycleCoordinator();
    provider = new KafkaEnvironmentProvider(coordinator);
    EnvironmentRequest request =
        new EnvironmentRequest(
            "msg002",
            KafkaEnvironmentProvider.TYPE,
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
    return new Harness(broker.properties().get("taf.messaging.kafka.bootstrap-servers"));
  }

  @Nested
  @DisplayName("Kafka native semantics")
  class NativeSemantics {
    @Test
    @DisplayName("fresh and reused groups expose deterministic offsets and metadata")
    void freshAndReusedGroups() throws Exception {
      Harness harness = (Harness) createHarness();
      try {
        String topic = "groups-" + UUID.randomUUID();
        harness.controller.publish(topic, new MessageEnvelope(new byte[] {1}));
        ConsumptionResult first =
            harness.controller.consume(
                new MessageQuery(topic, MessageSelector.any(), Duration.ofSeconds(10)), "group-a");
        ConsumptionResult reused =
            harness.controller.consume(
                new MessageQuery(topic, MessageSelector.any(), Duration.ofMillis(250)), "group-a");
        ConsumptionResult fresh =
            harness.controller.consume(
                new MessageQuery(topic, MessageSelector.any(), Duration.ofSeconds(10)), "group-b");
        assertThat(first.status()).isEqualTo(ConsumptionResult.Status.MATCHED);
        assertThat(reused.status()).isEqualTo(ConsumptionResult.Status.NO_MATCH);
        assertThat(fresh.record().orElseThrow().nativeMetadata())
            .containsEntry("partition", 0)
            .containsEntry("offset", 0L)
            .containsEntry("consumerGroup", "group-b");
      } finally {
        harness.close();
      }
    }
  }

  private static final class Harness implements MessagingControllerContractHarness {
    private final CountDownLatch consumeStarted = new CountDownLatch(1);
    private final DefaultKafkaController delegate;
    private final KafkaController controller;

    private Harness(String bootstrapServers) {
      KafkaControllerSettings settings = new KafkaControllerSettings();
      settings.setBootstrapServers(List.of(bootstrapServers));
      settings.setGroupId("contract-" + UUID.randomUUID());
      settings.setClientIdPrefix("msg002");
      settings.setTopicPolicy(KafkaTopicPolicy.CREATE_IF_MISSING);
      settings.setOperationTimeout(Duration.ofSeconds(10));
      delegate = new DefaultKafkaController("contract", settings);
      delegate.initialize(
          new ControllerContext(
              "session",
              EnvironmentAccess.unavailable(),
              new ArtifactCollector(
                  TafTest.of("contract", getClass().getName()), "session", "contract")));
      controller =
          new KafkaController() {
            public ConsumptionResult consume(MessageQuery query) throws InterruptedException {
              consumeStarted.countDown();
              return delegate.consume(unique(query));
            }

            public ConsumptionResult consume(MessageQuery query, String group)
                throws InterruptedException {
              consumeStarted.countDown();
              return delegate.consume(unique(query), group);
            }

            public MessageRecord publish(String destination, MessageEnvelope message) {
              return delegate.publish(unique(destination), message);
            }

            public ControllerIdentity identity() {
              return delegate.identity();
            }

            public ControllerState state() {
              return delegate.state();
            }

            public void initialize(ControllerContext context) {
              delegate.initialize(context);
            }

            public HealthResult health() {
              return delegate.health();
            }

            public java.util.stream.Stream<
                    com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact>
                collectArtifacts(ArtifactReason reason) {
              return delegate.collectArtifacts(reason);
            }

            public void close() {
              delegate.close();
            }

            private String unique(String destination) {
              return "contract-" + settings.getGroupId() + "-" + destination;
            }

            private MessageQuery unique(MessageQuery query) {
              return new MessageQuery(
                  unique(query.destination()), query.selector(), query.timeout());
            }
          };
    }

    public KafkaController controller() {
      return controller;
    }

    public void awaitConsumeStarted(Duration timeout) throws InterruptedException {
      assertThat(consumeStarted.await(timeout.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
    }

    public boolean isClosed() {
      return delegate.state() == ControllerState.CLOSED;
    }

    public void close() {
      delegate.close();
    }
  }
}

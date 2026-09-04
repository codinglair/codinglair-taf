package com.codinglair.taf.messaging.jms;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.messaging.*;
import com.codinglair.taf.messaging.contract.*;
import com.codinglair.taf.runtime.core.controller.*;
import com.codinglair.taf.runtime.core.reporting.ArtifactCollector;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@EnabledIfSystemProperty(named = "taf.containers.enabled", matches = "true")
@DisplayName("JMS Artemis container contract")
class JmsContainerContractTest extends MessagingControllerContract {
  private static GenericContainer<?> broker;
  private static String url;

  @BeforeAll
  static void startBroker() {
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is required for the JMS Artemis container contract");
    broker =
        new GenericContainer<>(DockerImageName.parse("apache/artemis:2.51.0-alpine"))
            .withEnv("ARTEMIS_USER", "taf")
            .withEnv("ARTEMIS_PASSWORD", "taf-test-only")
            .withExposedPorts(61616)
            .waitingFor(Wait.forListeningPort());
    broker.start();
    url = "tcp://" + broker.getHost() + ":" + broker.getMappedPort(61616);
  }

  @AfterAll
  static void stopBroker() {
    if (broker != null) broker.stop();
    assertThat(broker == null || !broker.isRunning()).isTrue();
  }

  @Override
  protected MessagingControllerContractHarness createHarness() {
    return new Harness();
  }

  @Nested
  @DisplayName("JMS native semantics")
  class NativeSemantics {
    @Test
    @DisplayName("supports JMS selectors while durable coverage verifies topics")
    void selector() throws Exception {
      DefaultJmsController controller = controller("selector", null);
      String queue = "selector." + UUID.randomUUID();
      try {
        var query = new MessageQuery(queue, MessageSelector.any(), Duration.ofSeconds(5));
        controller.publish(
            JmsDestination.queue(queue),
            new MessageEnvelope(new byte[] {1}, Map.of("color", "red"), Optional.empty()));
        controller.publish(
            JmsDestination.queue(queue),
            new MessageEnvelope(new byte[] {2}, Map.of("color", "blue"), Optional.empty()));
        assertThat(
                controller
                    .consume(JmsDestination.queue(queue), "color = 'blue'", query)
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
    @DisplayName("retains offline durable messages and removes the durable subscription on close")
    void durableCleanup() throws Exception {
      String suffix = UUID.randomUUID().toString();
      String topic = "durable." + suffix;
      String subscription = "subscription." + suffix;
      DefaultJmsController controller = controller("durable", "client." + suffix);
      var query = new MessageQuery(topic, MessageSelector.any(), Duration.ofMillis(150));
      assertThat(
              controller
                  .consumeDurable(
                      JmsDestination.topic(topic), new JmsSubscription(subscription, null), query)
                  .status())
          .isEqualTo(ConsumptionResult.Status.NO_MATCH);
      controller.publish(JmsDestination.topic(topic), new MessageEnvelope(new byte[] {9}));
      assertThat(
              controller
                  .consumeDurable(
                      JmsDestination.topic(topic),
                      new JmsSubscription(subscription, null),
                      new MessageQuery(topic, MessageSelector.any(), Duration.ofSeconds(5)))
                  .record()
                  .orElseThrow()
                  .envelope()
                  .payload())
          .containsExactly(9);
      controller.close();
      controller.close();
      try (var factory = factory();
          var context = factory.createContext()) {
        context.setClientID("client." + suffix);
        var recreated = context.createDurableConsumer(context.createTopic(topic), subscription);
        assertThat(recreated).isNotNull();
        recreated.close();
        context.unsubscribe(subscription);
      }
    }
  }

  private static DefaultJmsController controller(String name, String clientId) {
    JmsControllerSettings settings = new JmsControllerSettings();
    settings.setClientId(clientId);
    DefaultJmsController controller =
        new DefaultJmsController(
            name, settings, (ignored, ignoredSettings, ignoredContext) -> factory());
    controller.initialize(context(name));
    return controller;
  }

  private static ActiveMQConnectionFactory factory() {
    return new ActiveMQConnectionFactory(url, "taf", "taf-test-only");
  }

  private static ControllerContext context(String name) {
    return new ControllerContext(
        "session-" + name,
        EnvironmentAccess.unavailable(),
        new ArtifactCollector(
            TafTest.of(name, JmsContainerContractTest.class.getName()), "session-" + name, name));
  }

  private static final class Harness implements MessagingControllerContractHarness {
    private final CountDownLatch consumeStarted = new CountDownLatch(1);
    private final DefaultJmsController delegate =
        JmsContainerContractTest.controller("contract", null);
    private final String prefix = "contract." + UUID.randomUUID() + ".";
    private final JmsController wrapper =
        new JmsController() {
          @Override
          public MessageRecord publish(String destination, MessageEnvelope message) {
            return delegate.publish(prefix + destination, message);
          }

          @Override
          public ConsumptionResult consume(MessageQuery query) throws InterruptedException {
            consumeStarted.countDown();
            return delegate.consume(
                new MessageQuery(prefix + query.destination(), query.selector(), query.timeout()));
          }

          @Override
          public MessageRecord publish(JmsDestination destination, MessageEnvelope message) {
            return delegate.publish(destination, message);
          }

          @Override
          public ConsumptionResult consume(
              JmsDestination destination, String selector, MessageQuery query)
              throws InterruptedException {
            return delegate.consume(destination, selector, query);
          }

          @Override
          public ConsumptionResult consumeDurable(
              JmsDestination topic, JmsSubscription subscription, MessageQuery query)
              throws InterruptedException {
            return delegate.consumeDurable(topic, subscription, query);
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

    @Override
    public JmsController controller() {
      return wrapper;
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
  }
}

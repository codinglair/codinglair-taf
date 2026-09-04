package com.codinglair.taf.messaging.kafka;

import com.codinglair.taf.runtime.environment.*;
import java.util.List;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/** Governed disposable Kafka broker provider; controllers only consume its bootstrap endpoint. */
public final class KafkaEnvironmentProvider extends AbstractTestcontainersEnvironmentProvider {
  public static final EnvironmentType TYPE = new EnvironmentType("kafka");
  public static final DockerImageName DEFAULT_IMAGE =
      DockerImageName.parse("apache/kafka-native:4.2.0");
  private static final int KAFKA_PORT = 9092;

  public KafkaEnvironmentProvider(ContainerLifecycleCoordinator coordinator) {
    super(TYPE, definition(), ContainerImagePolicy.allow(DEFAULT_IMAGE), coordinator);
  }

  @Override
  public String id() {
    return "kafka-testcontainers";
  }

  @Override
  protected GenericContainer<?> newContainer(ContainerDefinition definition) {
    return new KafkaContainer(definition.image());
  }

  private static ContainerDefinition definition() {
    return new ContainerDefinition(
        DEFAULT_IMAGE,
        List.of(KAFKA_PORT),
        Map.of(),
        List.of(),
        Wait.forListeningPort(),
        Map.of(
            "taf.messaging.kafka.bootstrap-servers",
            container -> ((KafkaContainer) container).getBootstrapServers()),
        32_768);
  }
}

package com.codinglair.taf.messaging.rabbitmq;

import com.codinglair.taf.runtime.environment.*;
import java.util.List;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/** Governed disposable RabbitMQ provider; controllers only consume its connection properties. */
public final class RabbitEnvironmentProvider extends AbstractTestcontainersEnvironmentProvider {
  public static final EnvironmentType TYPE = new EnvironmentType("rabbitmq");
  public static final DockerImageName DEFAULT_IMAGE =
      DockerImageName.parse("rabbitmq:4.2.4-alpine");
  private static final int AMQP_PORT = 5672;

  public RabbitEnvironmentProvider(ContainerLifecycleCoordinator coordinator) {
    super(TYPE, definition(), ContainerImagePolicy.allow(DEFAULT_IMAGE), coordinator);
  }

  @Override
  public String id() {
    return "rabbitmq-testcontainers";
  }

  @Override
  protected GenericContainer<?> newContainer(ContainerDefinition definition) {
    return new RabbitMQContainer(definition.image());
  }

  private static ContainerDefinition definition() {
    return new ContainerDefinition(
        DEFAULT_IMAGE,
        List.of(AMQP_PORT),
        Map.of(),
        List.of(),
        Wait.forLogMessage(".*Server startup complete.*", 1),
        Map.of(
            "taf.messaging.rabbitmq.addresses",
            container -> container.getHost() + ":" + container.getMappedPort(AMQP_PORT),
            "taf.messaging.rabbitmq.username",
            container -> "guest",
            "taf.messaging.rabbitmq.password-reference",
            container -> "credential://profile/rabbitmq-testcontainers"),
        32_768);
  }
}

package com.codinglair.taf.messaging.aws.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.messaging.aws.eventbridge.EventBridgeControllerProperties;
import com.codinglair.taf.messaging.aws.sqs.SqsControllerProperties;
import com.codinglair.taf.messaging.aws.sqs.SqsReceiveRequest;
import com.codinglair.taf.messaging.aws.sqs.SqsSendRequest;
import com.codinglair.taf.runtime.core.controller.ControllerState;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.sqs.SqsClient;

class DefaultAwsControllersCleanupTest {
  @AfterEach
  void restoreInterruption() {
    Thread.interrupted();
  }

  @Test
  void sqsCleanupIsIdempotentAfterOperationFailure() {
    AtomicInteger closes = new AtomicInteger();
    SqsClient client =
        proxy(
            SqsClient.class,
            (method, _) -> {
              if (method.equals("close")) closes.incrementAndGet();
              if (method.equals("sendMessage")) throw new IllegalStateException("sdk detail");
              return null;
            });
    var controller = DefaultAwsControllers.sqs("orders", connection(), sqsSettings(), () -> client);
    controller.initialize(null);

    assertThatThrownBy(() -> controller.send(new SqsSendRequest("body", Map.of(), null)))
        .isInstanceOf(AwsControllerException.class)
        .hasMessageNotContaining("sdk detail");

    controller.close();
    controller.close();
    assertThat(closes).hasValue(1);
    assertThat(controller.state()).isEqualTo(ControllerState.CLOSED);
  }

  @Test
  void sqsCleanupIsIdempotentAfterInterruption() {
    AtomicInteger closes = new AtomicInteger();
    SqsClient client =
        proxy(
            SqsClient.class,
            (method, _) -> {
              if (method.equals("close")) closes.incrementAndGet();
              return null;
            });
    var controller = DefaultAwsControllers.sqs("orders", connection(), sqsSettings(), () -> client);
    controller.initialize(null);
    Thread.currentThread().interrupt();

    assertThatThrownBy(
            () ->
                controller.receive(new SqsReceiveRequest(Duration.ofSeconds(1), null, Map.of(), 1)))
        .isInstanceOf(InterruptedException.class);

    controller.close();
    controller.close();
    assertThat(closes).hasValue(1);
  }

  @Test
  void eventBridgeCleanupIsIdempotent() {
    AtomicInteger closes = new AtomicInteger();
    EventBridgeClient client =
        proxy(
            EventBridgeClient.class,
            (method, _) -> {
              if (method.equals("close")) closes.incrementAndGet();
              return null;
            });
    var controller =
        DefaultAwsControllers.eventbridge("events", connection(), eventSettings(), () -> client);
    controller.initialize(null);

    controller.close();
    controller.close();
    assertThat(closes).hasValue(1);
    assertThat(controller.state()).isEqualTo(ControllerState.CLOSED);
  }

  @Test
  void cleanupRemainsSafeAfterInitializationFailure() {
    var controller =
        DefaultAwsControllers.sqs(
            "orders",
            connection(),
            sqsSettings(),
            () -> {
              throw new IllegalStateException("client creation failed");
            });

    assertThatThrownBy(() -> controller.initialize(null))
        .isInstanceOf(AwsControllerException.class);
    controller.close();
    controller.close();
    assertThat(controller.state()).isEqualTo(ControllerState.CLOSED);
  }

  private static AwsConnectionProperties connection() {
    AwsConnectionProperties properties = new AwsConnectionProperties();
    properties.setRegion("us-east-1");
    properties.setEndpointMode(AwsEndpointMode.LOCALSTACK);
    properties.setEndpointOverride(URI.create("http://localhost:4566"));
    properties.setOwnershipMode(AwsOwnershipMode.TEST_OWNED);
    return properties;
  }

  private static SqsControllerProperties sqsSettings() {
    SqsControllerProperties properties = new SqsControllerProperties();
    properties.setQueue("http://localhost:4566/000/orders");
    return properties;
  }

  private static EventBridgeControllerProperties eventSettings() {
    EventBridgeControllerProperties properties = new EventBridgeControllerProperties();
    properties.setEventBus("events");
    return properties;
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, Invocation invocation) {
    return (T)
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (_, method, arguments) -> invocation.invoke(method.getName(), arguments));
  }

  @FunctionalInterface
  private interface Invocation {
    Object invoke(String method, Object[] arguments);
  }
}

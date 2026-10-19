package com.codinglair.taf.conformance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.runtime.core.TestSession;
import com.codinglair.taf.runtime.core.controller.ControllerIdentity;
import com.codinglair.taf.runtime.core.controller.ControllerState;
import com.codinglair.taf.runtime.core.controller.HealthResult;
import com.codinglair.taf.runtime.core.controller.TestController;
import com.codinglair.taf.runtime.core.lifecycle.InvocationDescriptor;
import com.codinglair.taf.runtime.core.lifecycle.InvocationOutcome;
import com.codinglair.taf.runtime.core.lifecycle.LifecycleViolationException;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionLifecycle;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Executable inventory proving every released controller contract uses the shared lifecycle guard.
 */
class CapabilityLifecycleCoverageTest {
  private static final Set<String> LIFECYCLE_METHODS =
      Set.of("identity", "state", "initialize", "health", "collectArtifacts", "close");
  private static final List<String> CONTROLLER_TYPES =
      List.of(
          "com.codinglair.taf.web.playwright.PlaywrightController",
          "com.codinglair.taf.api.rest.RestController",
          "com.codinglair.taf.database.DatabaseController",
          "com.codinglair.taf.messaging.kafka.KafkaController",
          "com.codinglair.taf.messaging.rabbitmq.RabbitController",
          "com.codinglair.taf.messaging.jms.JmsController",
          "com.codinglair.taf.messaging.aws.sqs.SqsController",
          "com.codinglair.taf.messaging.aws.eventbridge.EventBridgeController",
          "com.codinglair.taf.mobile.appium.AndroidController",
          "com.codinglair.taf.mobile.appium.service.AppleController");

  @Test
  void everyPublicCapabilityMethodRejectsClosedAndWrongInvocationBeforeDispatch() throws Exception {
    for (String className : CONTROLLER_TYPES) verifyController(className);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void verifyController(String className) throws Exception {
    Class<?> candidate = Class.forName(className);
    assertThat(candidate).isInterface();
    assertThat(TestController.class).isAssignableFrom(candidate);
    Class<? extends TestController> type = (Class<? extends TestController>) candidate;
    AtomicInteger dispatches = new AtomicInteger();
    TestController implementation = recordingImplementation(type, dispatches);
    TestSessionLifecycle lifecycle = new TestSessionLifecycle(TestSession::create);
    InvocationDescriptor owner = descriptor(className + "-owner");
    TestSession session = lifecycle.open(owner);
    session.getControllerRegistry().register((Class) type, "coverage", implementation);
    Object retained = session.getController((Class) type, "coverage");
    List<Method> operations = capabilityMethods(type);

    for (Method operation : operations) operation.invoke(retained, arguments(operation));
    int successfulDispatches = dispatches.get();
    assertThat(successfulDispatches).as(className).isEqualTo(operations.size());

    lifecycle.close(owner, InvocationOutcome.passed());
    for (Method operation : operations) assertLifecycleRejection(retained, operation);
    assertThat(dispatches)
        .as(className + " closed/stale dispatch count")
        .hasValue(successfulDispatches);

    InvocationDescriptor other = descriptor(className + "-other");
    lifecycle.open(other);
    for (Method operation : operations) assertLifecycleRejection(retained, operation);
    assertThat(dispatches)
        .as(className + " wrong-owner dispatch count")
        .hasValue(successfulDispatches);
    lifecycle.close(other, InvocationOutcome.passed());
  }

  private static List<Method> capabilityMethods(Class<?> type) {
    return Stream.of(type.getMethods())
        .filter(method -> !LIFECYCLE_METHODS.contains(method.getName()))
        .toList();
  }

  private static void assertLifecycleRejection(Object retained, Method operation) {
    assertThatThrownBy(() -> operation.invoke(retained, arguments(operation)))
        .isInstanceOf(InvocationTargetException.class)
        .cause()
        .isInstanceOf(LifecycleViolationException.class)
        .hasMessageContaining(LifecycleViolationException.ERROR_CODE);
  }

  private static Object[] arguments(Method method) {
    return Stream.of(method.getParameterTypes())
        .map(CapabilityLifecycleCoverageTest::defaultValue)
        .toArray();
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) return null;
    if (type == boolean.class) return false;
    if (type == char.class) return '\0';
    if (type == byte.class) return (byte) 0;
    if (type == short.class) return (short) 0;
    if (type == int.class) return 0;
    if (type == long.class) return 0L;
    if (type == float.class) return 0F;
    return 0D;
  }

  private static TestController recordingImplementation(
      Class<? extends TestController> type, AtomicInteger dispatches) {
    ControllerIdentity identity = new ControllerIdentity(type, "coverage");
    return (TestController)
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (proxy, method, arguments) -> {
              return switch (method.getName()) {
                case "identity" -> identity;
                case "state" -> ControllerState.READY;
                case "health" -> HealthResult.unknown("coverage");
                case "collectArtifacts" -> Stream.empty();
                case "initialize", "close" -> null;
                default -> {
                  dispatches.incrementAndGet();
                  yield defaultValue(method.getReturnType());
                }
              };
            });
  }

  private static InvocationDescriptor descriptor(String id) {
    return new InvocationDescriptor(id, id, "capability-coverage");
  }
}

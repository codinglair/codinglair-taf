package com.codinglair.taf.messaging.aws.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.messaging.aws.environment.AwsServiceEnvironmentContributor;
import com.codinglair.taf.messaging.aws.eventbridge.*;
import com.codinglair.taf.messaging.aws.sqs.*;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AwsArchitectureTest {
  @Test
  void publicContractsContainNoAwsSdkTypes() {
    List<Class<?>> contracts =
        List.of(
            SqsController.class,
            SqsSendRequest.class,
            SqsSendResult.class,
            SqsReceiveRequest.class,
            SqsMessage.class,
            ReceivedSqsMessage.class,
            SqsVisibility.class,
            SqsQueueCounts.class,
            SqsQueueDiagnostics.class,
            SqsMessageEvidence.class,
            EventBridgeController.class,
            EventPublishRequest.class,
            EventPublishEntryResult.class,
            EventPublishEvidence.class,
            EventPublishResult.class,
            EventRouteRequest.class,
            EventRouteResult.class,
            AwsServiceEnvironmentContributor.class);
    assertThat(
            contracts.stream()
                .flatMap(type -> Stream.of(type.getMethods()))
                .flatMap(AwsArchitectureTest::signatureTypes)
                .map(Class::getName))
        .noneMatch(name -> name.startsWith("software.amazon.awssdk"));
  }

  @Test
  void receiptHandleExistsOnlyOnSessionScopedMessage() {
    assertThat(ReceivedSqsMessage.class.getDeclaredMethods())
        .anyMatch(method -> method.getName().equals("receiptHandle"));
    assertThat(
            Stream.of(
                    SqsMessage.class,
                    SqsSendResult.class,
                    EventPublishResult.class,
                    EventPublishEntryResult.class)
                .flatMap(type -> Stream.of(type.getDeclaredMethods())))
        .noneMatch(method -> method.getName().toLowerCase().contains("receipthandle"));
  }

  private static Stream<Class<?>> signatureTypes(Method method) {
    return Stream.concat(Stream.of(method.getReturnType()), Stream.of(method.getParameterTypes()));
  }
}

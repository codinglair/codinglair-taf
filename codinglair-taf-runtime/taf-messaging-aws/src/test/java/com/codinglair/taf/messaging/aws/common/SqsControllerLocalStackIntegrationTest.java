package com.codinglair.taf.messaging.aws.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.messaging.aws.sqs.*;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

@EnabledIfSystemProperty(named = "taf.containers.enabled", matches = "true")
@DisplayName("SQS controller LocalStack contract")
class SqsControllerLocalStackIntegrationTest {
  @Test
  @DisplayName(
      "qualifies send, match, preservation, visibility, redelivery, acknowledgment and diagnostics")
  void qualifiesControllerLifecycle() throws Exception {
    try (var localstack =
        new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.8.1"))
            .withServices("sqs")) {
      localstack.start();
      var credentials =
          StaticCredentialsProvider.create(AwsBasicCredentials.create("localstack", "localstack"));
      try (SqsClient provisioner =
          SqsClient.builder()
              .region(Region.US_EAST_1)
              .endpointOverride(localstack.getEndpoint())
              .credentialsProvider(credentials)
              .build()) {
        String queue =
            provisioner.createQueue(builder -> builder.queueName("controller-orders")).queueUrl();
        String dlq =
            provisioner
                .createQueue(builder -> builder.queueName("controller-orders-dlq"))
                .queueUrl();
        String dlqArn =
            provisioner
                .getQueueAttributes(
                    builder -> builder.queueUrl(dlq).attributeNames(QueueAttributeName.QUEUE_ARN))
                .attributes()
                .get(QueueAttributeName.QUEUE_ARN);
        provisioner.setQueueAttributes(
            builder ->
                builder
                    .queueUrl(queue)
                    .attributes(
                        Map.of(
                            QueueAttributeName.REDRIVE_POLICY,
                            """
                            {"deadLetterTargetArn":"%s","maxReceiveCount":"3"}
                            """
                                .formatted(dlqArn)
                                .strip())));
        SqsController controller = controller(localstack, queue, dlq);
        try {
          controller.send(new SqsSendRequest("unrelated", Map.of(), "other"));
          controller.send(new SqsSendRequest("wanted", Map.of("kind", "order"), "run-1"));

          ReceivedSqsMessage wanted =
              controller.awaitMessage(
                  new SqsReceiveRequest(
                      Duration.ofSeconds(4), "run-1", Map.of("kind", "order"), 10));
          controller.assertBody(wanted, "wanted");
          assertThat(controller.evidence(wanted).correlationId()).isEqualTo("run-1");
          controller.changeVisibility(wanted, Duration.ZERO);

          ReceivedSqsMessage redelivered =
              controller.awaitMessage(
                  new SqsReceiveRequest(Duration.ofSeconds(4), "run-1", Map.of(), 10));
          assertThat(redelivered.message().receiveCount()).isGreaterThanOrEqualTo(2);
          controller.acknowledge(redelivered);

          ReceivedSqsMessage unrelated =
              controller.awaitMessage(
                  new SqsReceiveRequest(Duration.ofSeconds(4), "other", Map.of(), 10));
          assertThat(unrelated.message().body()).isEqualTo("unrelated");
          controller.acknowledge(unrelated);
          assertThat(controller.diagnostics().source().available()).isGreaterThanOrEqualTo(0);
          controller.assertNoMatchingMessage(
              new SqsReceiveRequest(Duration.ofSeconds(1), "absent", Map.of(), 10));

          controller.send(new SqsSendRequest("poison", Map.of(), "poison"));
          for (int attempt = 0; attempt < 3; attempt++) {
            ReceivedSqsMessage poison =
                controller.awaitMessage(
                    new SqsReceiveRequest(Duration.ofSeconds(4), "poison", Map.of(), 10));
            controller.changeVisibility(poison, Duration.ZERO);
          }
          long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
          boolean redriven = false;
          while (!redriven && System.nanoTime() < deadline) {
            provisioner.receiveMessage(
                builder -> builder.queueUrl(queue).waitTimeSeconds(0).maxNumberOfMessages(1));
            redriven =
                provisioner
                    .receiveMessage(
                        builder -> builder.queueUrl(dlq).waitTimeSeconds(1).maxNumberOfMessages(1))
                    .messages()
                    .stream()
                    .anyMatch(message -> message.body().equals("poison"));
          }
          assertThat(redriven).isTrue();
        } finally {
          controller.close();
        }
      }
    }
  }

  private static SqsController controller(
      LocalStackContainer localstack, String queue, String deadLetterQueue) {
    AwsConnectionProperties connection = new AwsConnectionProperties();
    connection.setRegion("us-east-1");
    connection.setEndpointMode(AwsEndpointMode.LOCALSTACK);
    connection.setEndpointOverride(localstack.getEndpoint());
    connection.setOwnershipMode(AwsOwnershipMode.TEST_OWNED);
    connection.getPolicy().setOperationTimeout(Duration.ofSeconds(5));
    connection.getPolicy().setPollInterval(Duration.ofMillis(25));
    SqsControllerProperties settings = new SqsControllerProperties();
    settings.setQueue(queue);
    settings.setDeadLetterQueue(deadLetterQueue);
    SqsController controller = DefaultAwsControllers.sqs("orders", connection, settings);
    controller.initialize(null);
    return controller;
  }
}

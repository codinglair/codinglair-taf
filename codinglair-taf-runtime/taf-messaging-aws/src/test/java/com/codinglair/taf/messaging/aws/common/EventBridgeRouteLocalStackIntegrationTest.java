package com.codinglair.taf.messaging.aws.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.messaging.aws.eventbridge.*;
import com.codinglair.taf.messaging.aws.sqs.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.RuleState;
import software.amazon.awssdk.services.eventbridge.model.Target;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

@EnabledIfSystemProperty(named = "taf.containers.enabled", matches = "true")
@DisplayName("EventBridge to SQS LocalStack contract")
class EventBridgeRouteLocalStackIntegrationTest {
  @Test
  @DisplayName("proves matching delivery, correlation, schema, and a bounded non-matching rule")
  void qualifiesRouteComposition() throws Exception {
    try (var localstack =
        new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.8.1"))
            .withServices("events", "sqs")) {
      localstack.start();
      var credentials =
          StaticCredentialsProvider.create(AwsBasicCredentials.create("localstack", "localstack"));
      try (SqsClient sqs =
              SqsClient.builder()
                  .region(Region.US_EAST_1)
                  .endpointOverride(localstack.getEndpoint())
                  .credentialsProvider(credentials)
                  .build();
          EventBridgeClient events =
              EventBridgeClient.builder()
                  .region(Region.US_EAST_1)
                  .endpointOverride(localstack.getEndpoint())
                  .credentialsProvider(credentials)
                  .build()) {
        String queue = sqs.createQueue(builder -> builder.queueName("route-orders")).queueUrl();
        String queueArn =
            sqs.getQueueAttributes(
                    builder -> builder.queueUrl(queue).attributeNames(QueueAttributeName.QUEUE_ARN))
                .attributes()
                .get(QueueAttributeName.QUEUE_ARN);
        sqs.setQueueAttributes(
            builder ->
                builder
                    .queueUrl(queue)
                    .attributes(
                        Map.of(
                            QueueAttributeName.POLICY,
                            "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"Service\":\"events.amazonaws.com\"},\"Action\":\"sqs:SendMessage\",\"Resource\":\""
                                + queueArn
                                + "\"}]}")));
        events.createEventBus(builder -> builder.name("orders-bus"));
        events.putRule(
            builder ->
                builder
                    .eventBusName("orders-bus")
                    .name("orders-rule")
                    .eventPattern("{\"source\":[\"orders.matching\"]}")
                    .state(RuleState.ENABLED));
        events.putTargets(
            builder ->
                builder
                    .eventBusName("orders-bus")
                    .rule("orders-rule")
                    .targets(Target.builder().id("orders-queue").arn(queueArn).build()));

        AwsConnectionProperties connection = connection(localstack);
        SqsController target = sqsController(connection, queue);
        EventBridgeController controller = eventController(connection);
        try {
          EventRouteResult routed =
              controller.verifyRoute(
                  new EventRouteRequest(event("orders.matching", "run-1"), Duration.ofSeconds(5)),
                  target);
          assertThat(routed.correlationId()).isEqualTo("run-1");
          assertThat(routed.targetEvidence().payload().content()).contains("orders.matching");

          controller.assertNotRouted(
              new EventRouteRequest(event("orders.ignored", "run-2"), Duration.ofSeconds(1)),
              target);
        } finally {
          controller.close();
          target.close();
        }
      }
    }
  }

  private static AwsConnectionProperties connection(LocalStackContainer localstack) {
    var connection = new AwsConnectionProperties();
    connection.setRegion("us-east-1");
    connection.setEndpointMode(AwsEndpointMode.LOCALSTACK);
    connection.setEndpointOverride(localstack.getEndpoint());
    connection.setOwnershipMode(AwsOwnershipMode.TEST_OWNED);
    connection.getPolicy().setOperationTimeout(Duration.ofSeconds(5));
    connection.getPolicy().setPollInterval(Duration.ofMillis(25));
    return connection;
  }

  private static SqsController sqsController(AwsConnectionProperties connection, String queue) {
    var settings = new SqsControllerProperties();
    settings.setQueue(queue);
    SqsController controller = DefaultAwsControllers.sqs("orders-target", connection, settings);
    controller.initialize(null);
    return controller;
  }

  private static EventBridgeController eventController(AwsConnectionProperties connection) {
    var settings = new EventBridgeControllerProperties();
    settings.setEventBus("orders-bus");
    settings.setTargetSqsController("orders-target");
    settings.setTargetIdentity("orders-queue");
    settings.setEnvelopeSchema(
        "{\"type\":\"object\",\"required\":[\"source\",\"detail-type\",\"detail\"]}");
    EventBridgeController controller =
        DefaultAwsControllers.eventbridge("orders", connection, settings);
    controller.initialize(null);
    return controller;
  }

  private static EventPublishRequest event(String source, String correlation) {
    return new EventPublishRequest(
        source,
        "order-created",
        "{\"orderId\":\"42\"}",
        List.of("arn:order:42"),
        Map.of("scenario", "route"),
        correlation,
        null);
  }
}

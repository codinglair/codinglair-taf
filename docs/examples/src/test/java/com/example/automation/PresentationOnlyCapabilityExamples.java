package com.example.automation;

import com.codinglair.taf.conformance.ConsumerProjectValidator;
import com.codinglair.taf.messaging.ConsumptionResult;
import com.codinglair.taf.messaging.Correlation;
import com.codinglair.taf.messaging.MessageEnvelope;
import com.codinglair.taf.messaging.MessageQuery;
import com.codinglair.taf.messaging.MessageSelector;
import com.codinglair.taf.messaging.jms.JmsConnectionFactoryProvider;
import com.codinglair.taf.messaging.jms.JmsController;
import com.codinglair.taf.messaging.jms.JmsDestination;
import com.codinglair.taf.messaging.kafka.KafkaController;
import com.codinglair.taf.messaging.rabbitmq.RabbitAcknowledgment;
import com.codinglair.taf.messaging.rabbitmq.RabbitController;
import com.codinglair.taf.messaging.rabbitmq.RabbitTopology;
import com.codinglair.taf.mobile.appium.AndroidController;
import com.codinglair.taf.runtime.core.TestSession;
import com.codinglair.taf.runtime.environment.EnvironmentResource;
import com.codinglair.taf.runtime.secret.ResolvedSecret;
import com.codinglair.taf.runtime.secret.SecretManager;
import com.codinglair.taf.runtime.secret.SecretRequestContext;
import com.codinglair.taf.virtualization.wiremock.FaultProfile;
import com.codinglair.taf.virtualization.wiremock.MappingHandle;
import com.codinglair.taf.virtualization.wiremock.VirtualMapping;
import com.codinglair.taf.virtualization.wiremock.WireMockVirtualization;
import com.codinglair.taf.virtualization.wiremock.WireMockVirtualizationFactory;
import jakarta.jms.ConnectionFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Presentation-purpose-only compile fixtures for DOC-001 snippets.
 *
 * <p>These methods verify released public signatures. They are not executable tests and do not
 * claim runtime verification against a device, broker, WireMock environment, or consumer project.
 */
final class PresentationOnlyCapabilityExamples {
  private PresentationOnlyCapabilityExamples() {}

  static void mobile(
      AndroidController android,
      SecretManager secrets,
      String passwordReference,
      TestSession session) {
    android.launch();
    var request =
        new SecretRequestContext(
            "MobileLoginWorkflow", session.getSessionId(), "integration", true);
    try (ResolvedSecret password = secrets.resolve(passwordReference, request)) {
      android.type(
          android.find("new UiSelector().resourceId(\"username\")"), password.useAsString());
    }
    android.tap(android.find("new UiSelector().resourceId(\"sign_in\")"));
    android.pageSource();
  }

  static void kafka(KafkaController kafka, String orderId, String sessionId)
      throws InterruptedException {
    Correlation correlation = new Correlation("orderId", orderId);
    MessageEnvelope message = envelope(orderId, correlation);
    kafka.publish("orders.events", message);
    ConsumptionResult consumed =
        kafka.consume(
            new MessageQuery(
                "orders.events", MessageSelector.correlated(correlation), Duration.ofSeconds(20)),
            "taf-orders-" + sessionId);
    consumed.record().orElseThrow().envelope().payload();
  }

  static void rabbit(RabbitController rabbit, String orderId) throws InterruptedException {
    RabbitTopology topology =
        new RabbitTopology("orders.test", "orders.completed.test", "orders.completed");
    Correlation correlation = new Correlation("orderId", orderId);
    rabbit.declareTopology(topology);
    rabbit.publish(topology, envelope(orderId, correlation));
    rabbit.consume(
        new MessageQuery(
            topology.queue(), MessageSelector.correlated(correlation), Duration.ofSeconds(20)),
        RabbitAcknowledgment.ACK);
  }

  static void jms(JmsController jms, String orderId) throws InterruptedException {
    JmsDestination queue = JmsDestination.queue("orders.completed.test");
    Correlation correlation = new Correlation("orderId", orderId);
    jms.publish(queue, envelope(orderId, correlation));
    jms.consume(
        queue,
        null,
        new MessageQuery(
            queue.name(), MessageSelector.correlated(correlation), Duration.ofSeconds(20)));
  }

  static JmsConnectionFactoryProvider jmsProvider(ApprovedJmsFactory factory) {
    return (controllerName, settings, context) ->
        factory.connectionFactoryFor(controllerName, settings.getClientId());
  }

  static MappingHandle wireMock(
      WireMockVirtualizationFactory factory, TestSession session, EnvironmentResource resource) {
    WireMockVirtualization virtualization = factory.create(session, resource, "integration");
    MappingHandle mapping =
        virtualization.add(
            new VirtualMapping(
                "GET",
                "/customers/42",
                200,
                Map.of("Content-Type", "application/json"),
                "{\"id\":42,\"status\":\"ACTIVE\"}",
                FaultProfile.none()));
    virtualization.verify("GET", "/customers/42", 1);
    return mapping;
  }

  static void conformance(Path projectRoot) {
    new ConsumerProjectValidator().validate(projectRoot).throwIfInvalid();
  }

  private static MessageEnvelope envelope(String orderId, Correlation correlation) {
    return new MessageEnvelope(
        orderId.getBytes(StandardCharsets.UTF_8),
        Map.of("content-type", "application/json"),
        Optional.of(correlation));
  }

  interface ApprovedJmsFactory {
    ConnectionFactory connectionFactoryFor(String controllerName, String clientId);
  }
}

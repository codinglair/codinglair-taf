package com.codinglair.taf.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.messaging.aws.common.AwsConnectionProperties;
import com.codinglair.taf.messaging.aws.common.AwsEndpointMode;
import com.codinglair.taf.messaging.aws.common.AwsOwnershipMode;
import com.codinglair.taf.messaging.aws.common.AwsProperties;
import com.codinglair.taf.messaging.aws.environment.AwsEnvironmentConfiguration;
import com.codinglair.taf.messaging.aws.environment.AwsResourceDescriptor;
import com.codinglair.taf.messaging.aws.environment.LocalStackEnvironmentProvider;
import com.codinglair.taf.messaging.aws.environment.LocalStackEnvironmentResource;
import com.codinglair.taf.messaging.aws.eventbridge.EventBridgeController;
import com.codinglair.taf.messaging.aws.eventbridge.EventBridgeControllerProperties;
import com.codinglair.taf.messaging.aws.eventbridge.EventPublishRequest;
import com.codinglair.taf.messaging.aws.eventbridge.EventRouteRequest;
import com.codinglair.taf.messaging.aws.sqs.SqsController;
import com.codinglair.taf.messaging.aws.sqs.SqsControllerProperties;
import com.codinglair.taf.runtime.core.TestSession;
import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import com.codinglair.taf.runtime.core.reporting.RedactionPipeline;
import com.codinglair.taf.runtime.core.reporting.ReporterDispatcher;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestReporter;
import com.codinglair.taf.runtime.core.reporting.impl.allure.AllureReporterAutoConfiguration;
import com.codinglair.taf.runtime.environment.EnvironmentMode;
import com.codinglair.taf.runtime.environment.EnvironmentRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

@DisplayName("AWS messaging staged consumer")
class AwsMessagingConsumerSmokeTest {
  private static final String PROFILE = "local";
  private static final String QUEUE = "orders-target";
  private static final String BUS = "orders-events";
  private static final String CANARY = "Bearer ex-110-001-secret-canary";
  private static final String ENVELOPE_SCHEMA =
      """
      {"type":"object","required":["source","detail-type","detail"],
       "properties":{"detail":{"type":"object","required":["orderId"]}}}
      """;

  @Nested
  @DisplayName("End-to-end routing")
  class EndToEndRouting {

    @Test
    @DisplayName("routes and acknowledges an event and publishes only sanitized Allure evidence")
    void routesEventWithPublicReleasedContracts() throws Exception {
      AwsProperties provisioning = provisioningConfiguration();
      var provider = new LocalStackEnvironmentProvider(EnvironmentMode.CONTAINER, provisioning);
      LocalStackEnvironmentResource environment =
          (LocalStackEnvironmentResource) provider.provision(environmentRequest());

      try {
        assertThat(environment.ownershipManifest().entries()).isNotEmpty();
        try (var context = applicationContext(environment.effectiveConfiguration());
            TestSession session = context.getBean(TestSessionFactory.class).create()) {
          SqsController sqs = session.getController(SqsController.class, QUEUE);
          EventBridgeController eventBridge =
              session.getController(EventBridgeController.class, BUS);
          var reporter =
              new ReporterDispatcher(context.getBean(TestReporter.class), new RedactionPipeline());
          TafTest test =
              TafTest.of(
                  "EventBridge to SQS released-artifact smoke",
                  AwsMessagingConsumerSmokeTest.class.getName());
          reporter.beginTest(test);

          var routed =
              eventBridge.verifyRoute(
                  new EventRouteRequest(
                      event("orders.created", "ex-110-match"), Duration.ofSeconds(10)),
                  sqs);
          assertThat(routed.correlationId()).isEqualTo("ex-110-match");
          assertThat(routed.targetEvidence().attributes()).isEmpty();
          assertThat(routed.targetEvidence().payload().content())
              .contains("orders.created", "order-created", "orderId", "ex-110-match");
          sqs.acknowledgeByMessageId(routed.targetEvidence().messageId());

          eventBridge.assertNotRouted(
              new EventRouteRequest(
                  event("orders.ignored", "ex-110-no-match"), Duration.ofSeconds(1)),
              sqs);

          session.getArtifactCollector().finalizeEvidence(reporter);
          reporter.endTest(test);
          assertThat(reporter.hasFailed()).isFalse();
          assertThat(reporter.reportedArtifacts()).isPositive();
        }

        assertSanitizedAllureResults();
      } finally {
        environment.cleanup();
        provider.cleanup();
      }

      assertThat(provider.activeResources()).isEmpty();
      assertThat(environment.diagnose().status().name()).isEqualTo("UNAVAILABLE");
    }
  }

  /**
   * Creates an EventBridge event carrying the correlation identifier and redaction canary used by
   * the smoke scenario.
   */
  private static EventPublishRequest event(String source, String correlationId) {
    return new EventPublishRequest(
        source,
        "order-created",
        "{\"orderId\":\"42\",\"authorization\":\"" + CANARY + "\"}",
        List.of("arn:example:order:42"),
        Map.of("scenario", "standalone-consumer", "authorization", CANARY),
        correlationId,
        null);
  }

  /** Creates the test-owned LocalStack resources required by the consumer scenario. */
  private static AwsProperties provisioningConfiguration() {
    var properties = new AwsProperties();
    properties.setEnabled(true);
    var profile = new AwsConnectionProperties();
    profile.setRegion("us-east-1");
    profile.setEndpointMode(AwsEndpointMode.LOCALSTACK);
    profile.setOwnershipMode(AwsOwnershipMode.TEST_OWNED);
    var queue = new SqsControllerProperties();
    queue.setQueue("orders");
    profile.getSqs().put(QUEUE, queue);
    var eventBridge = new EventBridgeControllerProperties();
    eventBridge.setEventBus("orders");
    eventBridge.setTargetSqsController(QUEUE);
    eventBridge.setTargetIdentity("orders-target");
    eventBridge.setEventPattern("{\"source\":[\"orders.created\"]}");
    profile.getEventbridge().put(BUS, eventBridge);
    properties.getProfiles().put(PROFILE, profile);
    return properties;
  }

  /** Creates the isolated environment request used to provision the LocalStack resources. */
  private static EnvironmentRequest environmentRequest() {
    return new EnvironmentRequest(
        PROFILE,
        LocalStackEnvironmentProvider.TYPE,
        EnvironmentMode.CONTAINER,
        Set.of(),
        Map.of("owner", "ex-110-001"),
        Duration.ofMinutes(2));
  }

  /**
   * Creates a consumer application context configured only from the provisioned environment's
   * public effective configuration.
   */
  private static AnnotationConfigApplicationContext applicationContext(
      AwsEnvironmentConfiguration environment) {
    Map<String, Object> properties =
        Map.ofEntries(
            Map.entry("taf.aws.enabled", "true"),
            Map.entry("taf.aws.profiles.local.region", environment.region()),
            Map.entry("taf.aws.profiles.local.endpoint-mode", "LOCALSTACK"),
            Map.entry("taf.aws.profiles.local.endpoint-override", environment.endpoint().toString()),
            Map.entry("taf.aws.profiles.local.ownership-mode", "TEST_OWNED"),
            Map.entry("taf.aws.profiles.local.policy.poll-interval", "25ms"),
            Map.entry(
                "taf.aws.profiles.local.sqs.orders-target.queue",
                resource(environment, "sqs-queue")),
            Map.entry(
                "taf.aws.profiles.local.eventbridge.orders-events.event-bus",
                resource(environment, "event-bus")),
            Map.entry(
                "taf.aws.profiles.local.eventbridge.orders-events.target-sqs-controller", QUEUE),
            Map.entry(
                "taf.aws.profiles.local.eventbridge.orders-events.target-identity",
                "orders-target"),
            Map.entry(
                "taf.aws.profiles.local.eventbridge.orders-events.event-pattern",
                "{\"source\":[\"orders.created\"]}"),
            Map.entry(
                "taf.aws.profiles.local.eventbridge.orders-events.envelope-schema",
                ENVELOPE_SCHEMA));
    var context = new AnnotationConfigApplicationContext();
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(new MapPropertySource("consumer", properties));
    context.register(
        TafRuntimeAutoConfiguration.class,
        com.codinglair.taf.messaging.aws.common.AwsAutoConfiguration.class,
        AllureReporterAutoConfiguration.class);
    context.refresh();
    return context;
  }

  /** Returns the physical identifier of the first provisioned resource with the requested type. */
  private static String resource(AwsEnvironmentConfiguration environment, String type) {
    return environment.resources().stream()
        .filter(resource -> resource.type().equals(type))
        .map(AwsResourceDescriptor::physicalId)
        .findFirst()
        .orElseThrow();
  }

  /** Verifies that generated Allure results contain redaction evidence but not the secret canary. */
  private static void assertSanitizedAllureResults() throws IOException {
    Path results = Path.of("target", "allure-results");
    assertThat(results).isDirectory();
    String published;
    try (var files = Files.list(results)) {
      published =
          files
              .filter(Files::isRegularFile)
              .map(AwsMessagingConsumerSmokeTest::read)
              .reduce("", String::concat);
    }
    assertThat(published).isNotBlank().doesNotContain(CANARY).contains("[REDACTED]");
  }

  /** Reads a generated Allure result, adapting checked I/O failures for stream processing. */
  private static String read(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot read generated Allure evidence", failure);
    }
  }
}

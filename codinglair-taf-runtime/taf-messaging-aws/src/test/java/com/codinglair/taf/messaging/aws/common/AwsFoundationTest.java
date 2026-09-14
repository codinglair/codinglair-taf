package com.codinglair.taf.messaging.aws.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codinglair.taf.messaging.aws.sqs.SqsControllerProperties;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.SqsException;

@DisplayName("Shared AWS foundation")
class AwsFoundationTest {
  @Nested
  @DisplayName("Failure classification")
  class FailureClassification {
    @Test
    @DisplayName("classifies throttling as transient and invalid requests as final")
    void classifiesServiceFailures() {
      var throttled = SqsException.builder().statusCode(429).message("secret=canary").build();
      var invalid = SqsException.builder().statusCode(400).build();

      assertThat(AwsFailureClassifier.classify(throttled))
          .isEqualTo(new AwsFailureClassifier.Classification(AwsFailureCategory.THROTTLING, true));
      assertThat(AwsFailureClassifier.classify(invalid).retryable()).isFalse();
    }

    @Test
    @DisplayName("does not disclose the SDK failure message")
    void sanitizesControllerFailure() {
      var failure =
          new AwsControllerException("SQS", "receive", new RuntimeException("token=canary"));

      assertThat(failure.getMessage()).doesNotContain("canary");
      assertThat(failure.category()).isEqualTo(AwsFailureCategory.AUTOMATION);
    }

    @Test
    @DisplayName("reports unavailable credentials without disclosing provider output")
    void sanitizesUnavailableCredentials() {
      var connection = new AwsConnectionProperties();
      connection.setRegion("us-east-1");
      connection.setEndpointMode(AwsEndpointMode.LOCALSTACK);
      connection.setEndpointOverride(URI.create("http://localhost:4566"));
      connection.setOwnershipMode(AwsOwnershipMode.TEST_OWNED);
      var settings = new SqsControllerProperties();
      settings.setQueue("http://localhost:4566/000/orders");
      var controller =
          DefaultAwsControllers.sqs(
              "orders",
              connection,
              settings,
              () -> {
                throw new IllegalStateException("credential token=canary-secret");
              });

      AwsControllerException failure =
          assertThrows(AwsControllerException.class, () -> controller.initialize(null));

      assertThat(failure.getMessage()).doesNotContain("canary-secret");
      assertThat(failure.operation()).isEqualTo("initialize");
    }
  }

  @Nested
  @DisplayName("Bounded execution")
  class BoundedExecution {
    @Test
    @DisplayName("retries transient safe work within the configured attempt bound")
    void retriesTransientWork() throws InterruptedException {
      var clock = new MutableClock();
      var attempts = new AtomicInteger();
      var policy = new AwsOperationPolicy();
      policy.setRetryAttempts(2);

      String result =
          new AwsOperationExecutor(clock)
              .execute(
                  "SQS",
                  "receive",
                  policy,
                  true,
                  () -> {
                    if (attempts.incrementAndGet() < 3)
                      throw new RuntimeException(new SocketTimeoutException());
                    return "done";
                  });

      assertThat(result).isEqualTo("done");
      assertThat(attempts).hasValue(3);
    }

    @Test
    @DisplayName("stops immediately for a non transient failure")
    void stopsForFinalFailure() {
      var attempts = new AtomicInteger();

      AwsControllerException failure =
          assertThrows(
              AwsControllerException.class,
              () ->
                  new AwsOperationExecutor(new MutableClock())
                      .execute(
                          "SQS",
                          "receive",
                          new AwsOperationPolicy(),
                          true,
                          () -> {
                            attempts.incrementAndGet();
                            throw SqsException.builder().statusCode(403).build();
                          }));

      assertThat(failure.category()).isEqualTo(AwsFailureCategory.AUTHORIZATION);
      assertThat(attempts).hasValue(1);
    }

    @Test
    @DisplayName("shares an attempt budget safely across concurrent callers")
    void boundsConcurrentAttempts() {
      var budget = new AwsOperationBudget(new MutableClock(), Duration.ofSeconds(5), 9);

      long acquired =
          IntStream.range(0, 100).parallel().filter(_ -> budget.tryAcquireAttempt()).count();

      assertThat(acquired).isEqualTo(10);
      assertThat(budget.attempts()).isEqualTo(10);
      assertThat(budget.exhausted()).isTrue();
    }
  }

  @Nested
  @DisplayName("Matching and evidence")
  class MatchingAndEvidence {
    @Test
    @DisplayName("matches correlation and only the selected attributes")
    void matchesSelectedValues() {
      assertThat(
              AwsAttributeMatcher.matches(
                  "c-1", Map.of("kind", "order"), "c-1", Map.of("kind", "order", "extra", "x")))
          .isTrue();
      assertThat(
              AwsAttributeMatcher.matches(
                  "c-1", Map.of("kind", "order"), "c-2", Map.of("kind", "order")))
          .isFalse();
    }

    @Test
    @DisplayName("redacts sensitive values and enforces UTF-8 evidence bytes")
    void sanitizesAndBoundsEvidence() {
      AwsEvidencePayload payload = AwsEvidenceSanitizer.payload("token=canary-秘密", 8);

      assertThat(payload.content().getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
          .isLessThanOrEqualTo(8);
      assertThat(payload.content()).doesNotContain("canary");
      assertThat(payload.truncated()).isTrue();
      assertThat(AwsEvidenceSanitizer.attributes(Map.of("authorization", "canary")))
          .containsEntry("authorization", "[REDACTED]");
    }
  }

  private static final class MutableClock implements AwsClock {
    private Instant now = Instant.parse("2026-01-01T00:00:00Z");

    public Instant now() {
      return now;
    }

    public void sleep(Duration duration) {
      now = now.plus(duration);
    }
  }
}

package com.codinglair.taf.runtime.core.condition;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Unit tests for AwaitableAssertion.
 *
 * @author Codinglair TAF Team
 */
class AwaitableAssertionTest {

  @Nested
  @DisplayName("Create")
  class CreateTests {

    @Test
    @DisplayName("create_withDefaultSettings")
    void create_withDefaultSettings() {
      AwaitableAssertion assertion = AwaitableAssertion.create("test", value -> true);

      assertEquals("test", assertion.getAssertionKey());
      assertNotNull(assertion.getTimeout());
      assertNotNull(assertion.getPollInterval());
      assertEquals(AwaitableAssertion.Status.PENDING, assertion.getStatus());
    }

    @Test
    @DisplayName("create_withCustomSettings")
    void create_withCustomSettings() {
      Duration timeout = Duration.ofSeconds(10);
      Duration pollInterval = Duration.ofMillis(50);

      AwaitableAssertion assertion =
          AwaitableAssertion.create("test", value -> true, timeout, pollInterval);

      assertEquals("test", assertion.getAssertionKey());
      assertEquals(timeout, assertion.getTimeout());
      assertEquals(pollInterval, assertion.getPollInterval());
    }
  }

  @Nested
  @DisplayName("Success Cases")
  class SuccessTests {

    @Test
    @DisplayName("await_succeeds_immediately")
    void await_succeeds_immediately() {
      AwaitableAssertion assertion =
          AwaitableAssertion.create("immediate", value -> value.equals("ready"));

      AwaitableAssertion.AwaitableAssertionResult result = assertion.await(() -> "ready");

      assertEquals(AwaitableAssertion.Status.SUCCESS, result.getStatus());
      assertNotNull(result.getLastObservedValue());
      assertEquals("ready", result.getLastObservedValue());
      assertTrue(result.isSuccessful());
    }

    @Test
    @DisplayName("await_succeeds_afterPolling")
    void await_succeeds_afterPolling() {
      AtomicInteger pollCount = new AtomicInteger(0);
      AwaitableAssertion assertion =
          AwaitableAssertion.create(
              "delayed",
              value -> {
                int count = pollCount.incrementAndGet();
                return count >= 3;
              },
              Duration.ofSeconds(1),
              Duration.ofMillis(10));

      AwaitableAssertion.AwaitableAssertionResult result = assertion.await(() -> null);

      assertEquals(AwaitableAssertion.Status.SUCCESS, result.getStatus());
      assertTrue(result.getPollCount() >= 3);
    }
  }

  @Nested
  @DisplayName("Timeout Cases")
  class TimeoutTests {

    @Test
    @DisplayName("await_times_out")
    @Timeout(value = 5, unit = java.util.concurrent.TimeUnit.SECONDS)
    void await_times_out() {
      AwaitableAssertion assertion =
          AwaitableAssertion.create(
              "timeout-test", value -> false, Duration.ofMillis(100), Duration.ofMillis(50));

      AwaitableAssertion.AwaitableAssertionResult result = assertion.await(() -> null);

      assertEquals(AwaitableAssertion.Status.TIMEOUT, result.getStatus());
      assertNull(result.getLastObservedValue());
      assertTrue(result.isTimedOut());
      assertFalse(result.isSuccessful());
    }

    @Test
    @DisplayName("await_times_out_withLastObservedValue")
    @Timeout(value = 5, unit = java.util.concurrent.TimeUnit.SECONDS)
    void await_times_out_withLastObservedValue() {
      AwaitableAssertion assertion =
          AwaitableAssertion.create(
              "timeout-with-value",
              value -> value.equals("success"),
              Duration.ofMillis(100),
              Duration.ofMillis(50));

      AwaitableAssertion.AwaitableAssertionResult result = assertion.await(() -> "initial");

      assertEquals(AwaitableAssertion.Status.TIMEOUT, result.getStatus());
      assertEquals("initial", result.getLastObservedValue());
      assertTrue(result.isTimedOut());
    }

    @Test
    @DisplayName("await_times_out_withTimingInformation")
    @Timeout(value = 5, unit = java.util.concurrent.TimeUnit.SECONDS)
    void await_times_out_withTimingInformation() {
      AwaitableAssertion assertion =
          AwaitableAssertion.create(
              "timeout-timing", value -> false, Duration.ofMillis(100), Duration.ofMillis(50));

      AwaitableAssertion.AwaitableAssertionResult result = assertion.await(() -> null);

      assertEquals(AwaitableAssertion.Status.TIMEOUT, result.getStatus());
      assertNotNull(result.getEndTime());
      assertTrue(result.getEndTime().isAfter(result.getStartTime()));
      assertTrue(result.getPollCount() > 0);
    }
  }

  @Nested
  @DisplayName("Failure Cases")
  class FailureTests {

    @Test
    @DisplayName("await_fails_onException")
    void await_fails_onException() {
      AwaitableAssertion assertion =
          AwaitableAssertion.create(
              "exception-test",
              value -> {
                throw new RuntimeException("Test exception");
              },
              Duration.ofSeconds(1),
              Duration.ofMillis(10));

      AwaitableAssertion.AwaitableAssertionResult result = assertion.await(() -> null);

      assertEquals(AwaitableAssertion.Status.FAILED, result.getStatus());
      assertTrue(result.getErrors().size() > 0);
      assertEquals(1, result.getErrors().size());
      assertTrue(result.isFailed());
    }

    @Test
    @DisplayName("await_fails_and_tracksErrors")
    void await_fails_and_tracksErrors() {
      AwaitableAssertion assertion =
          AwaitableAssertion.create(
              "error-tracking",
              value -> {
                throw new IllegalArgumentException("Invalid state");
              },
              Duration.ofSeconds(1),
              Duration.ofMillis(10));

      AwaitableAssertion.AwaitableAssertionResult result = assertion.await(() -> null);

      assertNotNull(result.getErrors());
      assertEquals(1, result.getErrors().size());
      Throwable error = result.getErrors().get(0);
      assertEquals(IllegalArgumentException.class, error.getClass());
    }
  }

  @Nested
  @DisplayName("Result Properties")
  class ResultPropertiesTests {

    @Test
    @DisplayName("result_hasAllRequiredFields")
    void result_hasAllRequiredFields() {
      AwaitableAssertion assertion = AwaitableAssertion.create("key", value -> true);
      AwaitableAssertion.AwaitableAssertionResult result = assertion.await(() -> "value");

      assertNotNull(result.getKey());
      assertNotNull(result.getStatus());
      assertNotNull(result.getEndTime());
      assertNotNull(result.getLastObservedValue());
      assertNotNull(result.getMessage());
    }

    @Test
    @DisplayName("result_toString_includesAllFields")
    void result_toString_includesAllFields() {
      AwaitableAssertion assertion = AwaitableAssertion.create("key", value -> true);
      AwaitableAssertion.AwaitableAssertionResult result = assertion.await(() -> "value");

      String resultString = result.toString();

      assertTrue(resultString.contains("key"));
      assertTrue(resultString.contains("status"));
      assertTrue(resultString.contains("endTime"));
      assertTrue(resultString.contains("message"));
    }

    @Test
    @DisplayName("result_isSuccessful_returnsCorrectValue")
    void result_isSuccessful_returnsCorrectValue() {
      AwaitableAssertion assertion = AwaitableAssertion.create("key", value -> true);
      AwaitableAssertion.AwaitableAssertionResult successResult = assertion.await(() -> "success");

      AwaitableAssertion assertionFail = AwaitableAssertion.create("key-fail", value -> false);
      AwaitableAssertion.AwaitableAssertionResult failResult = assertionFail.await(() -> null);

      assertTrue(successResult.isSuccessful());
      assertFalse(failResult.isSuccessful());
    }

    @Test
    @DisplayName("result_isTimedOut_returnsCorrectValue")
    void result_isTimedOut_returnsCorrectValue() {
      AwaitableAssertion assertion =
          AwaitableAssertion.create(
              "key", value -> false, Duration.ofMillis(10), Duration.ofMillis(10));
      AwaitableAssertion.AwaitableAssertionResult result = assertion.await(() -> null);

      assertTrue(result.isTimedOut());
    }

    @Test
    @DisplayName("result_isFailed_returnsCorrectValue")
    void result_isFailed_returnsCorrectValue() {
      AwaitableAssertion assertion =
          AwaitableAssertion.create(
              "key",
              value -> {
                throw new RuntimeException();
              },
              Duration.ofSeconds(1),
              Duration.ofMillis(10));
      AwaitableAssertion.AwaitableAssertionResult result = assertion.await(() -> null);

      assertTrue(result.isFailed());
    }
  }

  @Nested
  @DisplayName("Message Generation")
  class MessageGenerationTests {

    @Test
    @DisplayName("success_message_includesPollCountAndValue")
    void success_message_includesPollCountAndValue() {
      AtomicInteger pollCount = new AtomicInteger(0);
      AwaitableAssertion assertion =
          AwaitableAssertion.create(
              "success-message",
              value -> {
                int count = pollCount.incrementAndGet();
                return count >= 2;
              },
              Duration.ofSeconds(5),
              Duration.ofMillis(10));

      AwaitableAssertion.AwaitableAssertionResult result = assertion.await(() -> null);

      String message = result.getMessage();
      assertTrue(message.contains("Assertion succeeded"));
      assertTrue(message.contains("polls"));
    }

    @Test
    @DisplayName("timeout_message_includesLastObservedValue")
    void timeout_message_includesLastObservedValue() {
      AwaitableAssertion assertion =
          AwaitableAssertion.create(
              "timeout-message", value -> false, Duration.ofMillis(100), Duration.ofMillis(50));

      AwaitableAssertion.AwaitableAssertionResult result = assertion.await(() -> "initial");

      String message = result.getMessage();
      assertTrue(message.contains("timed out"));
      assertTrue(message.contains("Last observed value"));
    }

    @Test
    @DisplayName("failure_message_includesException")
    void failure_message_includesException() {
      AwaitableAssertion assertion =
          AwaitableAssertion.create(
              "exception-message",
              value -> {
                throw new RuntimeException("Test error");
              },
              Duration.ofSeconds(1),
              Duration.ofMillis(10));

      AwaitableAssertion.AwaitableAssertionResult result = assertion.await(() -> null);

      String message = result.getMessage();
      assertTrue(message.contains("Assertion failed"));
      assertTrue(message.contains("RuntimeException"));
    }
  }

  @Nested
  @DisplayName("Concurrent Execution")
  class ConcurrentExecutionTests {

    @Test
    @DisplayName("parallelAssertions_doNotInterfere")
    void parallelAssertions_doNotInterfere() {
      int numAssertions = 5;
      final AtomicInteger[] pollCounts = new AtomicInteger[numAssertions];
      final AwaitableAssertion[] assertions = new AwaitableAssertion[numAssertions];

      for (int i = 0; i < numAssertions; i++) {
        pollCounts[i] = new AtomicInteger(0);
        final int index = i;
        assertions[i] =
            AwaitableAssertion.create(
                "assertion-" + i,
                value -> {
                  int count = pollCounts[index].incrementAndGet();
                  return count >= 2;
                },
                Duration.ofSeconds(1),
                Duration.ofMillis(10));
      }

      for (int i = 0; i < numAssertions; i++) {
        AwaitableAssertion.AwaitableAssertionResult result = assertions[i].await(() -> null);

        assertEquals(AwaitableAssertion.Status.SUCCESS, result.getStatus());
        assertTrue(result.getPollCount() >= 2);
      }
    }
  }
}

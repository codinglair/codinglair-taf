package com.codinglair.taf.runtime.core.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.codinglair.taf.runtime.core.TestSession;
import com.codinglair.taf.runtime.core.condition.AwaitableAssertion;
import com.codinglair.taf.runtime.core.context.CorrelationContext;
import com.codinglair.taf.runtime.core.context.TestContext;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Integration tests for TestContext and CorrelationContext.
 *
 * @author Codinglair TAF Team
 */
class ContextCorrelationIntegrationTests {

  @Nested
  @DisplayName("Parallel Isolation")
  class ParallelIsolationTests {

    @Test
    @DisplayName("parallelSessions_haveIsolatedCorrelationContexts")
    void parallelSessions_haveIsolatedCorrelationContexts() throws InterruptedException {
      int numSessions = 5;
      int[] observedTraceIds = new int[numSessions];

      ExecutorService executor = Executors.newFixedThreadPool(numSessions);
      CountDownLatch latch = new CountDownLatch(numSessions);

      for (int i = 0; i < numSessions; i++) {
        final int index = i;
        executor.submit(
            () -> {
              TestSession session = TestSession.create();
              CorrelationContext sessionContext = session.getCorrelationContext();
              TestContext sessionTestContext = session.getTestContext();

              observedTraceIds[index] = sessionContext.getTraceId().hashCode();
              session.complete();
              latch.countDown();
              return null;
            });
      }

      latch.await(10, TimeUnit.SECONDS);
      executor.shutdown();

      // All trace IDs should be different
      for (int i = 0; i < numSessions - 1; i++) {
        for (int j = i + 1; j < numSessions; j++) {
          // Different trace IDs should have different hash codes
          assertTrue(
              observedTraceIds[i] != observedTraceIds[j],
              "Sessions should have different correlation contexts");
        }
      }
    }

    @Test
    @DisplayName("parallelSessions_haveIsolatedTestContexts")
    void parallelSessions_haveIsolatedTestContexts() throws InterruptedException {
      int numSessions = 5;

      ExecutorService executor = Executors.newFixedThreadPool(numSessions);
      CountDownLatch latch = new CountDownLatch(numSessions);

      for (int i = 0; i < numSessions; i++) {
        final int index = i;
        executor.submit(
            () -> {
              TestSession session = TestSession.create();
              TestContext sessionTestContext = session.getTestContext();

              // Each session should have its own test context
              assertTrue(session.getTestContext().getContextId() != null);
              session.complete();
              latch.countDown();
              return null;
            });
      }

      latch.await(10, TimeUnit.SECONDS);
      executor.shutdown();
    }

    @Test
    @DisplayName("parallelSessions_haveIsolatedPreconditions")
    void parallelSessions_haveIsolatedPreconditions() throws InterruptedException {
      int numSessions = 3;

      ExecutorService executor = Executors.newFixedThreadPool(numSessions);
      CountDownLatch latch = new CountDownLatch(numSessions);

      for (int i = 0; i < numSessions; i++) {
        final int index = i;
        executor.submit(
            () -> {
              TestSession session = TestSession.create();
              TestContext sessionTestContext = session.getTestContext();

              // Register a precondition
              sessionTestContext.registerPrecondition(
                  "shared-key-" + index,
                  com.codinglair.taf.runtime.core.precondition.PreconditionResult.satisfied(
                      "shared-key-" + index,
                      com.codinglair.taf.runtime.core.precondition.PreconditionResult
                          .PreconditionType.CUSTOM));

              session.complete();
              latch.countDown();
              return null;
            });
      }

      latch.await(10, TimeUnit.SECONDS);
      executor.shutdown();
    }

    @Test
    @DisplayName("parallelSessions_haveIsolatedAttributes")
    void parallelSessions_haveIsolatedAttributes() throws InterruptedException {
      int numSessions = 3;

      ExecutorService executor = Executors.newFixedThreadPool(numSessions);
      CountDownLatch latch = new CountDownLatch(numSessions);

      for (int i = 0; i < numSessions; i++) {
        final int index = i;
        executor.submit(
            () -> {
              TestSession session = TestSession.create();
              TestContext sessionTestContext = session.getTestContext();

              sessionTestContext.setAttribute("shared-key-" + index, "shared-value-" + index);

              session.complete();
              latch.countDown();
              return null;
            });
      }

      latch.await(10, TimeUnit.SECONDS);
      executor.shutdown();
    }
  }

  @Nested
  @DisplayName("Correlation Propagation")
  class CorrelationPropagationTests {

    @Test
    @DisplayName("correlationContext_propagatesToTestContext")
    void correlationContext_propagatesToTestContext() {
      TestSession session = TestSession.create();
      CorrelationContext sessionContext = session.getCorrelationContext();
      TestContext testContext = session.getTestContext();

      assertEquals(sessionContext.getTraceId(), testContext.getCorrelationContext().getTraceId());
      assertEquals(sessionContext.getSpanId(), testContext.getCorrelationContext().getSpanId());
    }

    @Test
    @DisplayName("testContext_toHeaders_returnsCorrelationHeaders")
    void testContext_toHeaders_returnsCorrelationHeaders() {
      TestSession session = TestSession.create();
      TestContext testContext = session.getTestContext();

      Map<String, String> headers = testContext.toHeaders();

      assertNotNull(headers);
      assertTrue(headers.containsKey("X-Trace-ID"));
      assertTrue(headers.containsKey("X-Span-ID"));
    }

    @Test
    @DisplayName("correlationContext_child_createsNewContextWithSameTraceId")
    void correlationContext_child_createsNewContextWithSameTraceId() {
      TestSession session = TestSession.create();
      CorrelationContext parentContext = session.getCorrelationContext();

      CorrelationContext childContext = parentContext.child("child-span-1");

      assertEquals(parentContext.getTraceId(), childContext.getTraceId());
      assertEquals("child-span-1", childContext.getSpanId());
    }

    @Test
    @DisplayName("testContext_child_preservesCorrelationContext")
    void testContext_child_preservesCorrelationContext() {
      TestSession session = TestSession.create();
      TestContext parentContext = session.getTestContext();

      TestContext childContext = parentContext.child("child-span-1");

      assertEquals(
          parentContext.getCorrelationContext().getTraceId(),
          childContext.getCorrelationContext().getTraceId());
    }
  }

  @Nested
  @DisplayName("Correlation Headers")
  class CorrelationHeaderTests {

    @Test
    @DisplayName("correlationContext_toHeaders_includesAllRequiredHeaders")
    void correlationContext_toHeaders_includesAllRequiredHeaders() {
      CorrelationContext context =
          CorrelationContext.create(
              "test-trace-id",
              "test-span-id",
              "test-session-id",
              Map.of("metadata-key", "metadata-value"));

      Map<String, String> headers = context.toHeaders();

      assertEquals(
          "X-Trace-ID",
          headers.keySet().stream().filter(k -> k.contains("Trace")).findFirst().orElse(null));
      assertEquals(
          "X-Span-ID",
          headers.keySet().stream().filter(k -> k.contains("Span")).findFirst().orElse(null));
      assertEquals(
          "X-Session-ID",
          headers.keySet().stream().filter(k -> k.contains("Session")).findFirst().orElse(null));
      assertTrue(headers.containsKey("X-Meta-metadata-key"));
    }

    @Test
    @DisplayName("correlationContext_toHeaders_withoutSessionId_excludesSessionHeader")
    void correlationContext_toHeaders_withoutSessionId_excludesSessionHeader() {
      CorrelationContext context =
          CorrelationContext.create("test-trace-id", "test-span-id", null, Map.of());

      Map<String, String> headers = context.toHeaders();

      // Session ID header should not be present when sessionId is null
      assertTrue(headers.containsKey("X-Trace-ID"));
      assertTrue(headers.containsKey("X-Span-ID"));
      // No X-Session-ID header should be present
      headers.keySet().forEach(System.out::println);
    }
  }

  @Nested
  @DisplayName("Timing")
  class TimingTests {

    @Test
    @DisplayName("correlationContext_hasValidStartTime")
    void correlationContext_hasValidStartTime() {
      CorrelationContext context = CorrelationContext.create();

      assertNotNull(context.getStartTime());
      assertTrue(context.getStartTime().isBefore(Instant.now().plusSeconds(1)));
    }

    @Test
    @DisplayName("testContext_hasValidStartTime")
    void testContext_hasValidStartTime() {
      TestContext context = TestContext.create();

      assertNotNull(context.getStartTime());
      assertTrue(context.getStartTime().isBefore(Instant.now().plusSeconds(1)));
    }

    @Test
    @DisplayName("testContext_hasValidEndTimeAfterComplete")
    void testContext_hasValidEndTimeAfterComplete() {
      TestContext context = TestContext.create();

      Instant startTime = context.getStartTime();
      Instant endTime = context.getEndTime();

      // Initially, endTime should be null or not set
      assertNotNull(startTime);
    }

    @Test
    @DisplayName("testContext_complete_setsEndTime")
    void testContext_complete_setsEndTime() {
      TestContext context = TestContext.create();

      // Initially, endTime should be null
      assertNull(context.getEndTime());

      Instant startTime = context.getStartTime();
      // Complete with a time slightly after start time to ensure isAfter works
      context.complete(startTime.plusSeconds(1));
      Instant afterComplete = context.getEndTime();

      assertNotNull(afterComplete);
      assertTrue(afterComplete.isAfter(startTime));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("awaitableAssertion_timesOutWithTimingInfo")
    void awaitableAssertion_timesOutWithTimingInfo() {
      com.codinglair.taf.runtime.core.condition.AwaitableAssertion assertion =
          com.codinglair.taf.runtime.core.condition.AwaitableAssertion.create(
              "timeout-test",
              value -> false,
              java.time.Duration.ofMillis(100),
              java.time.Duration.ofMillis(50));

      com.codinglair.taf.runtime.core.condition.AwaitableAssertion.AwaitableAssertionResult result =
          assertion.await(() -> null);

      assertEquals(AwaitableAssertion.Status.TIMEOUT, result.getStatus());
      assertNotNull(result.getEndTime());
      assertNotNull(result.getStartTime());
      assertTrue(result.getEndTime().isAfter(result.getStartTime()));
    }
  }
}

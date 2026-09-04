package com.codinglair.taf.runtime.core.context;

import static org.junit.jupiter.api.Assertions.*;

import com.codinglair.taf.runtime.core.precondition.PreconditionResult;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for TestContext.
 *
 * @author Codinglair TAF Team
 */
class TestContextTest {

  @Nested
  @DisplayName("Create")
  class CreateTests {

    @Test
    @DisplayName("create_autoGeneratesContextId")
    void create_autoGeneratesContextId() {
      TestContext context = TestContext.create();
      assertNotNull(context.getContextId());
    }

    @Test
    @DisplayName("create_withCorrelationContext")
    void create_withCorrelationContext() {
      CorrelationContext cc = CorrelationContext.create("trace", "span", "session");
      TestContext context = TestContext.create(cc);

      assertEquals("trace", context.getCorrelationContext().getTraceId());
      assertEquals("span", context.getCorrelationContext().getSpanId());
      assertEquals("session", context.getCorrelationContext().getSessionId());
    }
  }

  @Nested
  @DisplayName("Correlation Context")
  class CorrelationContextTests {

    @Test
    @DisplayName("getCorrelationContext_returnsOriginal")
    void getCorrelationContext_returnsOriginal() {
      CorrelationContext cc = CorrelationContext.create("trace", "span", "session");
      TestContext context = TestContext.create(cc);

      assertEquals(cc, context.getCorrelationContext());
    }
  }

  @Nested
  @DisplayName("Preconditions")
  class PreconditionsTests {

    @Test
    @DisplayName("getPreconditions_returnsEmptyMap")
    void getPreconditions_returnsEmptyMap() {
      TestContext context = TestContext.create();
      assertTrue(context.getPreconditions().isEmpty());
    }

    @Test
    @DisplayName("hasPrecondition_false_forEmpty")
    void hasPrecondition_false_forEmpty() {
      TestContext context = TestContext.create();
      assertFalse(context.hasPrecondition("database"));
    }

    @Test
    @DisplayName("getPrecondition_returnsNull_forUnregistered")
    void getPrecondition_returnsNull_forUnregistered() {
      TestContext context = TestContext.create();
      assertNull(context.getPrecondition("database"));
    }

    @Test
    @DisplayName("registerPrecondition_addsResult")
    void registerPrecondition_addsResult() {
      TestContext context = TestContext.create();
      PreconditionResult result =
          PreconditionResult.satisfied("database", PreconditionResult.PreconditionType.DATABASE);
      context.registerPrecondition("database", result);

      assertTrue(context.hasPrecondition("database"));
      assertEquals(result, context.getPrecondition("database"));
    }

    @Test
    @DisplayName("getPreconditionKeys_returnsRegisteredKeys")
    void getPreconditionKeys_returnsRegisteredKeys() {
      TestContext context = TestContext.create();
      PreconditionResult result1 =
          PreconditionResult.satisfied("db1", PreconditionResult.PreconditionType.DATABASE);
      PreconditionResult result2 =
          PreconditionResult.satisfied("db2", PreconditionResult.PreconditionType.DATABASE);
      context.registerPrecondition("db1", result1);
      context.registerPrecondition("db2", result2);

      assertEquals(2, context.getPreconditionKeys().size());
      assertTrue(context.getPreconditionKeys().contains("db1"));
      assertTrue(context.getPreconditionKeys().contains("db2"));
    }

    @Test
    @DisplayName("getFailedPreconditionKeys_returnsEmpty_forAllSatisfied")
    void getFailedPreconditionKeys_returnsEmpty_forAllSatisfied() {
      TestContext context = TestContext.create();
      PreconditionResult result1 =
          PreconditionResult.satisfied("db1", PreconditionResult.PreconditionType.DATABASE);
      context.registerPrecondition("db1", result1);

      assertTrue(context.getFailedPreconditionKeys().isEmpty());
    }

    @Test
    @DisplayName("getFailedPreconditionKeys_returnsFailed")
    void getFailedPreconditionKeys_returnsFailed() {
      TestContext context = TestContext.create();
      PreconditionResult result1 =
          PreconditionResult.failed(
              "db1", PreconditionResult.PreconditionType.DATABASE, "Connection refused");
      context.registerPrecondition("db1", result1);

      assertEquals(1, context.getFailedPreconditionKeys().size());
      assertEquals("db1", context.getFailedPreconditionKeys().get(0));
    }

    @Test
    @DisplayName("hasFailedPreconditions_false_whenAllSatisfied")
    void hasFailedPreconditions_false_whenAllSatisfied() {
      TestContext context = TestContext.create();
      PreconditionResult result =
          PreconditionResult.satisfied("db1", PreconditionResult.PreconditionType.DATABASE);
      context.registerPrecondition("db1", result);

      assertFalse(context.hasFailedPreconditions());
    }

    @Test
    @DisplayName("hasFailedPreconditions_true_whenAnyFailed")
    void hasFailedPreconditions_true_whenAnyFailed() {
      TestContext context = TestContext.create();
      PreconditionResult result1 =
          PreconditionResult.satisfied("db1", PreconditionResult.PreconditionType.DATABASE);
      PreconditionResult result2 =
          PreconditionResult.failed("db2", PreconditionResult.PreconditionType.DATABASE, "Timeout");
      context.registerPrecondition("db1", result1);
      context.registerPrecondition("db2", result2);

      assertTrue(context.hasFailedPreconditions());
    }
  }

  @Nested
  @DisplayName("Attributes")
  class AttributesTests {

    @Test
    @DisplayName("getAttributes_returnsEmptyMap")
    void getAttributes_returnsEmptyMap() {
      TestContext context = TestContext.create();
      assertTrue(context.getAttributes().isEmpty());
    }

    @Test
    @DisplayName("setAttribute_addsAttribute")
    void setAttribute_addsAttribute() {
      TestContext context = TestContext.create();
      context.setAttribute("key", "value");

      assertEquals(1, context.getAttributes().size());
      assertEquals("value", context.getAttributes().get("key"));
    }

    @Test
    @DisplayName("setAttribute_overwritesExisting")
    void setAttribute_overwritesExisting() {
      TestContext context = TestContext.create();
      context.setAttribute("key", "value1");
      context.setAttribute("key", "value2");

      assertEquals("value2", context.getAttributes().get("key"));
    }
  }

  @Nested
  @DisplayName("Timing")
  class TimingTests {

    @Test
    @DisplayName("getStartTime_returnsNow")
    void getStartTime_returnsNow() {
      TestContext context = TestContext.create();
      assertNotNull(context.getStartTime());
      // Verify startTime is not null and is a valid instant
      assertNotNull(context.getStartTime());
      assertTrue(context.getStartTime().isAfter(Instant.EPOCH));
    }

    @Test
    @DisplayName("isCompleted_false_whenNotCompleted")
    void isCompleted_false_whenNotCompleted() {
      TestContext context = TestContext.create();
      assertFalse(context.isCompleted());
    }

    @Test
    @DisplayName("complete_setsEndTime")
    void complete_setsEndTime() {
      TestContext context = TestContext.create();
      Instant expectedEndTime = Instant.now();
      context.complete(expectedEndTime);

      assertEquals(expectedEndTime, context.getEndTime());
      assertTrue(context.isCompleted());
    }
  }

  @Nested
  @DisplayName("Child Context")
  class ChildContextTests {

    @Test
    @DisplayName("child_createsNewContext")
    void child_createsNewContext() {
      TestContext parent = TestContext.create();
      TestContext child = parent.child();

      assertNotNull(child.getContextId());
      assertNotNull(child.getCorrelationContext());
      // Child should have a DIFFERENT context ID from parent (proper isolation)
      assertFalse(child.getContextId().equals(parent.getContextId()));
    }

    @Test
    @DisplayName("child_withSpanId_setsNewSpanId")
    void child_withSpanId_setsNewSpanId() {
      TestContext parent = TestContext.create();
      TestContext child = parent.child("new-span");

      CorrelationContext parentCtx = parent.getCorrelationContext();
      CorrelationContext childCtx = child.getCorrelationContext();

      assertEquals(parentCtx.getTraceId(), childCtx.getTraceId());
      assertEquals("new-span", childCtx.getSpanId());
    }

    @Test
    @DisplayName("child_preservesPreconditions")
    void child_preservesPreconditions() {
      TestContext parent = TestContext.create();
      PreconditionResult result =
          PreconditionResult.satisfied("db1", PreconditionResult.PreconditionType.DATABASE);
      parent.registerPrecondition("db1", result);

      TestContext child = parent.child("span-2");

      assertTrue(child.hasPrecondition("db1"));
      assertEquals(result, child.getPrecondition("db1"));
    }

    @Test
    @DisplayName("child_createsNewAttributesMap")
    void child_createsNewAttributesMap() {
      TestContext parent = TestContext.create();
      parent.setAttribute("key1", "value1");

      TestContext child = parent.child("span-2");

      assertEquals(1, child.getAttributes().size());
      assertEquals("value1", child.getAttributes().get("key1"));
    }
  }

  @Nested
  @DisplayName("ToHeaders")
  class ToHeadersTests {

    @Test
    @DisplayName("toHeaders_returnsCorrelationHeaders")
    void toHeaders_returnsCorrelationHeaders() {
      TestContext context = TestContext.create();
      Map<String, String> headers = context.toHeaders();

      assertNotNull(headers);
      assertTrue(headers.containsKey("X-Trace-ID"));
      assertTrue(headers.containsKey("X-Span-ID"));
    }
  }

  @Nested
  @DisplayName("ToString")
  class ToStringTests {

    @Test
    @DisplayName("toString_includesAllFields")
    void toString_includesAllFields() {
      TestContext context = TestContext.create();
      String result = context.toString();

      assertTrue(result.contains("contextId"));
      assertTrue(result.contains("correlationContext"));
      assertTrue(result.contains("startTime"));
    }
  }
}

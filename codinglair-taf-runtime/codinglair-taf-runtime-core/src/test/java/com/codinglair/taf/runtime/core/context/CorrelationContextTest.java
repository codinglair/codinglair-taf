package com.codinglair.taf.runtime.core.context;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for CorrelationContext.
 *
 * @author Codinglair TAF Team
 */
class CorrelationContextTest {

  @Nested
  @DisplayName("Create")
  class CreateTests {

    @Test
    @DisplayName("create_autoGeneratesIds")
    void create_autoGeneratesIds() {
      CorrelationContext context = CorrelationContext.create();
      assertNotNull(context.getTraceId());
      assertNotNull(context.getSpanId());
      assertNotNull(context.getStartTime());
    }

    @Test
    @DisplayName("create_withExplicitIds")
    void create_withExplicitIds() {
      String traceId = "test-trace-123";
      String spanId = "test-span-456";
      String sessionId = "test-session-789";

      CorrelationContext context = CorrelationContext.create(traceId, spanId, sessionId);

      assertEquals(traceId, context.getTraceId());
      assertEquals(spanId, context.getSpanId());
      assertEquals(sessionId, context.getSessionId());
    }

    @Test
    @DisplayName("create_withMetadata")
    void create_withMetadata() {
      Map<String, String> metadata = Map.of("key1", "value1", "key2", "value2");

      CorrelationContext context = CorrelationContext.create("trace", "span", null, metadata);

      assertEquals(metadata, context.getMetadata());
    }

    @Test
    @DisplayName("create_withTags")
    void create_withTags() {
      Map<String, String> tags = Map.of("tag1", "val1", "tag2", "val2");

      CorrelationContext context =
          CorrelationContext.create("trace", "span", null, java.util.Collections.emptyMap(), tags);

      assertEquals(tags, context.getTags());
    }
  }

  @Nested
  @DisplayName("Child Context")
  class ChildContextTests {

    @Test
    @DisplayName("child_createsNewContextWithSameTraceId")
    void child_createsNewContextWithSameTraceId() {
      CorrelationContext parent =
          CorrelationContext.create("trace-1", "span-1", "session-1", Map.of());
      CorrelationContext child = parent.child("span-2");

      assertEquals("trace-1", child.getTraceId());
      assertEquals("span-2", child.getSpanId());
    }

    @Test
    @DisplayName("child_preservesMetadata")
    void child_preservesMetadata() {
      CorrelationContext parent =
          CorrelationContext.create("trace-1", "span-1", "session-1", Map.of("meta", "value"));
      CorrelationContext child = parent.child("span-2");

      assertEquals(Map.of("meta", "value"), child.getMetadata());
    }

    @Test
    @DisplayName("child_withTags_addsTags")
    void child_withTags_addsTags() {
      Map<String, String> parentTags = Map.of("tag1", "val1");
      CorrelationContext parent =
          CorrelationContext.create("trace-1", "span-1", "session-1", Map.of(), parentTags);
      Map<String, String> newTags = Map.of("tag2", "val2");
      CorrelationContext child = parent.child("span-2", newTags);

      assertEquals(2, child.getTags().size());
      assertTrue(child.getTags().containsKey("tag1"));
      assertTrue(child.getTags().containsKey("tag2"));
    }
  }

  @Nested
  @DisplayName("Headers")
  class HeadersTests {

    @Test
    @DisplayName("toHeaders_includesRequiredHeaders")
    void toHeaders_includesRequiredHeaders() {
      CorrelationContext context =
          CorrelationContext.create("trace-1", "span-1", "session-1", Map.of());
      Map<String, String> headers = context.toHeaders();

      assertEquals("trace-1", headers.get("X-Trace-ID"));
      assertEquals("span-1", headers.get("X-Span-ID"));
      assertEquals("session-1", headers.get("X-Session-ID"));
    }

    @Test
    @DisplayName("toHeaders_withoutSessionId")
    void toHeaders_withoutSessionId() {
      CorrelationContext context = CorrelationContext.create("trace-1", "span-1", null, Map.of());
      Map<String, String> headers = context.toHeaders();

      assertEquals("trace-1", headers.get("X-Trace-ID"));
      assertEquals("span-1", headers.get("X-Span-ID"));
      assertNull(headers.get("X-Session-ID"));
    }
  }

  @Nested
  @DisplayName("ToString")
  class ToStringTests {

    @Test
    @DisplayName("toString_includesAllFields")
    void toString_includesAllFields() {
      CorrelationContext context =
          CorrelationContext.create("trace", "span", "session", Map.of("meta", "value"));
      String result = context.toString();

      assertTrue(result.contains("traceId"));
      assertTrue(result.contains("spanId"));
      assertTrue(result.contains("sessionId"));
      assertTrue(result.contains("startTime"));
    }
  }
}

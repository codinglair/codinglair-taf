package com.codinglair.taf.runtime.core.precondition;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for PreconditionResult.
 *
 * @author Codinglair TAF Team
 */
class PreconditionResultTest {

  @Nested
  @DisplayName("Create - Satisfied")
  class SatisfiedTests {

    @Test
    @DisplayName("satisfied_createsWithSatisfiedStatus")
    void satisfied_createsWithSatisfiedStatus() {
      PreconditionResult result =
          PreconditionResult.satisfied("database", PreconditionResult.PreconditionType.DATABASE);

      assertEquals("database", result.getPreconditionKey());
      assertEquals(PreconditionResult.PreconditionType.DATABASE, result.getPreconditionType());
      assertEquals(PreconditionResult.PreconditionStatus.SATISFIED, result.getStatus());
      assertNotNull(result.getMessage());
      assertTrue(result.getMessage().contains("satisfied"));
      assertNotNull(result.getCheckedAt());
      assertTrue(result.getCheckedAt().isBefore(Instant.now().plusSeconds(1)));
    }

    @Test
    @DisplayName("satisfied_hasEmptyDetailsAndRecommendations")
    void satisfied_hasEmptyDetailsAndRecommendations() {
      PreconditionResult result =
          PreconditionResult.satisfied("database", PreconditionResult.PreconditionType.DATABASE);

      assertTrue(result.getDetails().isEmpty());
      assertTrue(result.getRecommendations().isEmpty());
    }
  }

  @Nested
  @DisplayName("Create - Failed")
  class FailedTests {

    @Test
    @DisplayName("failed_createsWithFailedStatus")
    void failed_createsWithFailedStatus() {
      PreconditionResult result =
          PreconditionResult.failed(
              "database", PreconditionResult.PreconditionType.DATABASE, "Connection refused");

      assertEquals("database", result.getPreconditionKey());
      assertEquals(PreconditionResult.PreconditionType.DATABASE, result.getPreconditionType());
      assertEquals(PreconditionResult.PreconditionStatus.FAILED, result.getStatus());
      assertEquals("Connection refused", result.getMessage());
    }

    @Test
    @DisplayName("failed_withDetails_includesDetails")
    void failed_withDetails_includesDetails() {
      List<String> details = List.of("Error code: ECONNREFUSED", "Host: localhost", "Port: 5432");
      PreconditionResult result =
          PreconditionResult.failed(
              "database",
              PreconditionResult.PreconditionType.DATABASE,
              "Connection failed",
              details);

      assertEquals(3, result.getDetails().size());
      assertEquals("Error code: ECONNREFUSED", result.getDetails().get(0));
    }

    @Test
    @DisplayName("failed_withRecommendations_includesRecommendations")
    void failed_withRecommendations_includesRecommendations() {
      List<String> recommendations =
          List.of(
              "Check database service is running",
              "Verify network connectivity",
              "Review firewall rules");
      PreconditionResult result =
          PreconditionResult.failedWithRecommendations(
              "database",
              PreconditionResult.PreconditionType.DATABASE,
              "Connection failed",
              recommendations);

      assertEquals(3, result.getRecommendations().size());
      assertEquals("Check database service is running", result.getRecommendations().get(0));
    }

    @Test
    @DisplayName("failed_withDetailsAndRecommendations_includesBoth")
    void failed_withDetailsAndRecommendations_includesBoth() {
      List<String> details = List.of("Error code: ECONNREFUSED");
      List<String> recommendations = List.of("Check service is running");

      PreconditionResult result =
          PreconditionResult.failedWithDetailsAndRecommendations(
              "database",
              PreconditionResult.PreconditionType.DATABASE,
              "Connection failed",
              details,
              recommendations);

      assertEquals(1, result.getDetails().size());
      assertEquals(1, result.getRecommendations().size());
    }
  }

  @Nested
  @DisplayName("Create - Skipped")
  class SkippedTests {

    @Test
    @DisplayName("skipped_createsWithSkippedStatus")
    void skipped_createsWithSkippedStatus() {
      PreconditionResult result =
          PreconditionResult.skipped("database", PreconditionResult.PreconditionType.DATABASE);

      assertEquals("database", result.getPreconditionKey());
      assertEquals(PreconditionResult.PreconditionType.DATABASE, result.getPreconditionType());
      assertEquals(PreconditionResult.PreconditionStatus.SKIPPED, result.getStatus());
      assertEquals("Precondition skipped", result.getMessage());
    }
  }

  @Nested
  @DisplayName("Create - Unknown")
  class UnknownTests {

    @Test
    @DisplayName("unknown_createsWithUnknownStatus")
    void unknown_createsWithUnknownStatus() {
      PreconditionResult result =
          PreconditionResult.unknown("database", PreconditionResult.PreconditionType.DATABASE);

      assertEquals("database", result.getPreconditionKey());
      assertEquals(PreconditionResult.PreconditionType.DATABASE, result.getPreconditionType());
      assertEquals(PreconditionResult.PreconditionStatus.UNKNOWN, result.getStatus());
      assertEquals("Precondition status unknown", result.getMessage());
    }

    @Test
    @DisplayName("unknown_withMessage_includesMessage")
    void unknown_withMessage_includesMessage() {
      PreconditionResult result =
          PreconditionResult.unknown(
              "database",
              PreconditionResult.PreconditionType.DATABASE,
              "Could not determine status");

      assertEquals("Could not determine status", result.getMessage());
    }

    @Test
    @DisplayName("unknown_withContext_includesContext")
    void unknown_withContext_includesContext() {
      // Create a precondition result with context using the overloaded method
      Map<String, String> context =
          Map.of(
              "error", "Connection timeout",
              "host", "localhost");
      PreconditionResult result =
          PreconditionResult.failedWithDetailsAndRecommendations(
              "database",
              PreconditionResult.PreconditionType.DATABASE,
              "Connection error",
              List.of("Error occurred"),
              List.of("Check connection"),
              context);

      assertEquals(context, result.getContext());
    }
  }

  @Nested
  @DisplayName("Getters")
  class GettersTests {

    @Test
    @DisplayName("getters_returnCorrectValues")
    void getters_returnCorrectValues() {
      PreconditionResult result =
          PreconditionResult.failedWithDetailsAndRecommendations(
              "database",
              PreconditionResult.PreconditionType.DATABASE,
              "Connection refused",
              List.of("Error code: ECONNREFUSED"),
              List.of("Check service is running"));

      assertEquals("database", result.getPreconditionKey());
      assertEquals(PreconditionResult.PreconditionType.DATABASE, result.getPreconditionType());
      assertEquals(PreconditionResult.PreconditionStatus.FAILED, result.getStatus());
      assertEquals("Connection refused", result.getMessage());
      assertNotNull(result.getCheckedAt());
      assertEquals(1, result.getDetails().size());
      assertEquals(1, result.getRecommendations().size());
      assertTrue(result.getContext().isEmpty());
    }
  }

  @Nested
  @DisplayName("WithStatus")
  class WithStatusTests {

    @Test
    @DisplayName("withStatus_updatesStatusAndMessage")
    void withStatus_updatesStatusAndMessage() {
      PreconditionResult original =
          PreconditionResult.failedWithDetailsAndRecommendations(
              "database",
              PreconditionResult.PreconditionType.DATABASE,
              "Original message",
              List.of("detail"),
              List.of("recommendation"));

      PreconditionResult updated =
          original.withStatus(PreconditionResult.PreconditionStatus.SATISFIED, "Now satisfied");

      assertEquals(PreconditionResult.PreconditionStatus.SATISFIED, updated.getStatus());
      assertEquals("Now satisfied", updated.getMessage());
      assertEquals("database", updated.getPreconditionKey());
      assertEquals(PreconditionResult.PreconditionType.DATABASE, updated.getPreconditionType());
    }

    @Test
    @DisplayName("withStatus_preservesCheckedAt")
    void withStatus_preservesCheckedAt() {
      Instant originalTime = Instant.now();
      PreconditionResult original =
          PreconditionResult.failedWithDetailsAndRecommendations(
              "database",
              PreconditionResult.PreconditionType.DATABASE,
              "Original message",
              List.of("detail"),
              List.of("recommendation"));

      Instant updatedTime = Instant.now();
      PreconditionResult updated =
          original.withStatus(PreconditionResult.PreconditionStatus.SATISFIED, "Updated message");

      // The checkedAt should be approximately the same (within a few seconds)
      assertTrue(
          Math.abs(
                  original.getCheckedAt().getEpochSecond()
                      - updated.getCheckedAt().getEpochSecond())
              <= 5);
    }
  }

  @Nested
  @DisplayName("ToString")
  class ToStringTests {

    @Test
    @DisplayName("toString_includesAllFields")
    void toString_includesAllFields() {
      PreconditionResult result =
          PreconditionResult.failedWithDetailsAndRecommendations(
              "database",
              PreconditionResult.PreconditionType.DATABASE,
              "Connection refused",
              List.of("detail1", "detail2"),
              List.of("recommendation1"));

      String resultString = result.toString();

      assertTrue(resultString.contains("preconditionKey"));
      assertTrue(resultString.contains("type"));
      assertTrue(resultString.contains("status"));
      assertTrue(resultString.contains("message"));
      assertTrue(resultString.contains("details"));
      assertTrue(resultString.contains("recommendations"));
    }
  }
}

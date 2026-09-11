package com.codinglair.taf.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Tool response contract")
class ToolResponseTest {
  @Nested
  @DisplayName("Result references")
  class ResultReferences {
    @Test
    @DisplayName("rejects null result references")
    void rejectsNullReferences() {
      assertThrows(
          NullPointerException.class,
          () ->
              new ToolResponse(
                  "request",
                  ToolResponse.Status.COMPLETED,
                  WorkflowOutcome.SUCCEEDED,
                  "complete",
                  null,
                  null));
    }

    @Test
    @DisplayName("accepts an immutable empty result-reference list")
    void acceptsEmptyReferences() {
      var response =
          new ToolResponse(
              "request",
              ToolResponse.Status.COMPLETED,
              WorkflowOutcome.SUCCEEDED,
              "complete",
              null,
              List.of());
      assertThat(response.resultReferences()).isEmpty();
      assertThrows(
          UnsupportedOperationException.class,
          () -> response.resultReferences().add("taf://evidence/job/item"));
    }
  }
}

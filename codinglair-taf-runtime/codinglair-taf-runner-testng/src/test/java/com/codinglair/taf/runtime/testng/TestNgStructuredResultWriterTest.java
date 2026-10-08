package com.codinglair.taf.runtime.testng;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TestNG structured result JSON")
class TestNgStructuredResultWriterTest {

  @Nested
  @DisplayName("Wire-format preservation")
  class WireFormatPreservation {

    @Test
    @DisplayName(
        "Preserves exact field order, attempt order, null semantics, escaping, and redaction")
    void preservesExactJsonContract() {
      var first =
          new TestNgAttemptResult(
              1,
              Instant.EPOCH,
              TestNgAttemptResult.Status.PASSED,
              null,
              List.of(
                  TestArtifact.of("first", "text", "password=hunter2", null),
                  TestArtifact.of("second\nline", "json", "{\"ok\":true}", "application/json")),
              null);
      var second =
          new TestNgAttemptResult(
              2,
              Instant.parse("2026-10-05T12:34:56Z"),
              TestNgAttemptResult.Status.SKIPPED,
              null,
              List.of(),
              null);

      String json =
          new TestNgStructuredResultWriter()
              .writeJson(
                  new TestNgExecutionResult(
                      "test-id",
                      "quoted \"name\"",
                      "example.ContractTest",
                      List.of(first, second)));

      assertThat(json)
          .isEqualTo(
              """
              {"testId":"test-id","testName":"quoted \\"name\\"","className":"example.ContractTest","attempts":[{"attemptNumber":1,"completedAt":"1970-01-01T00:00:00Z","status":"PASSED","failure":null,"artifacts":[{"name":"first","type":"text","contentType":"","content":"****"},{"name":"second\\nline","type":"json","contentType":"application/json","content":"{\\"ok\\":true}"}],"failureAnalysis":null},{"attemptNumber":2,"completedAt":"2026-10-05T12:34:56Z","status":"SKIPPED","failure":null,"artifacts":[],"failureAnalysis":null}]}
              """
                  .strip());
    }
  }
}

package com.codinglair.taf.runtime.core.reporting.impl.allure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Deterministic Allure evidence validator")
class AllureEvidenceValidatorTest {
  @TempDir Path temporary;

  @Nested
  @DisplayName("Valid evidence")
  class ValidEvidence {
    @Test
    @DisplayName("accepts exact attempts, nested ownership, sanitized bytes, and embedded sources")
    void acceptsCompleteEvidence() throws Exception {
      Fixture fixture = fixture();

      var report =
          new AllureEvidenceValidator()
              .validate(fixture.results(), fixture.report(), fixture.matrix());

      assertThat(report.valid()).isTrue();
      assertThat(report.resultCount()).isEqualTo(2);
      assertThat(report.attachmentCount()).isEqualTo(3);
      assertThat(report.violations()).isEmpty();
    }

    @Test
    @DisplayName("accepts the nested and shared fixture containers emitted by Allure TestNG")
    void acceptsRealAllureTestNgContainerGraph() throws Exception {
      Fixture fixture = fixture();
      Files.writeString(
          fixture.results().resolve("test-container.json"),
          container("test-container", "attempt-1"));
      Files.writeString(
          fixture.results().resolve("suite-parent-container.json"),
          container("suite-parent-container", "test-container"));
      Files.writeString(
          fixture.results().resolve("before-method-container.json"),
          container("before-method-container", "attempt-1"));
      Files.writeString(
          fixture.results().resolve("after-method-container.json"),
          container("after-method-container", "attempt-1"));

      var report =
          new AllureEvidenceValidator()
              .validate(fixture.results(), fixture.report(), fixture.matrix());

      assertThat(report.valid()).isTrue();
      assertThat(report.violations()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Adversarial evidence")
  class AdversarialEvidence {
    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("mutations")
    @DisplayName("rejects deterministic corruption with a specific code")
    void rejectsMutation(String expectedCode, Mutation mutation) throws Exception {
      Fixture fixture = fixture();
      mutation.apply(fixture);

      var report =
          new AllureEvidenceValidator()
              .validate(fixture.results(), fixture.report(), fixture.matrix());

      assertThat(report.valid()).isFalse();
      assertThat(report.violations())
          .extracting(AllureEvidenceValidator.Violation::code)
          .contains(expectedCode);
    }

    static Stream<Arguments> mutations() {
      return Stream.of(
          Arguments.of(
              "attachment.dangling",
              (Mutation) fixture -> Files.delete(fixture.results().resolve("request.txt"))),
          Arguments.of(
              "attachment.orphan",
              (Mutation)
                  fixture -> Files.writeString(fixture.results().resolve("orphan.txt"), "orphan")),
          Arguments.of(
              "attachment.reference.duplicate",
              (Mutation)
                  fixture ->
                      Files.writeString(
                          fixture.results().resolve("attempt-2-result.json"),
                          result("attempt-2", "failed", "request.txt", "REST attempt"))),
          Arguments.of(
              "attachment.owner.mismatch",
              (Mutation) fixture -> Files.writeString(fixture.matrix(), matrix("wrong step", 2))),
          Arguments.of(
              "result.status.mismatch",
              (Mutation)
                  fixture ->
                      Files.writeString(
                          fixture.results().resolve("attempt-2-result.json"),
                          result("attempt-2", "passed", "failure.txt", "REST attempt"))),
          Arguments.of(
              "content.unsanitized",
              (Mutation)
                  fixture ->
                      Files.writeString(
                          fixture.results().resolve("request.txt"), "token=secret-canary")),
          Arguments.of(
              "report.attachment.unavailable",
              (Mutation)
                  fixture -> Files.writeString(fixture.report(), "<html>request.txt only</html>")),
          Arguments.of(
              "attachment.path.unsafe",
              (Mutation)
                  fixture ->
                      Files.writeString(
                          fixture.results().resolve("attempt-1-result.json"),
                          result("attempt-1", "passed", "../outside.txt", "REST attempt"))),
          Arguments.of(
              "result.owner.duplicate",
              (Mutation)
                  fixture ->
                      Files.writeString(
                          fixture.results().resolve("suite-container.json"),
                          "{\"uuid\":\"suite\",\"children\":[\"attempt-1\",\"attempt-1\"]}")),
          Arguments.of(
              "container.child.dangling",
              (Mutation)
                  fixture ->
                      Files.writeString(
                          fixture.results().resolve("suite-container.json"),
                          container("suite", "unknown-result-or-container"))));
    }
  }

  private Fixture fixture() throws Exception {
    Path results = Files.createDirectories(temporary.resolve("allure-results"));
    Files.writeString(
        results.resolve("request.txt"),
        "method=GET\nstatus=200\nsessionId=s1\ntestId=t1\nstepId=op1\nattempt=1\nsha256=abc",
        StandardCharsets.UTF_8);
    Files.writeString(
        results.resolve("failure.txt"),
        "phase=connection\nsessionId=s1\ntestId=t1\nstepId=op2\nattempt=2\nsha256=def",
        StandardCharsets.UTF_8);
    Files.writeString(results.resolve("setup.txt"), "setup=ready", StandardCharsets.UTF_8);
    Files.writeString(
        results.resolve("attempt-1-result.json"),
        result("attempt-1", "passed", "request.txt", "REST attempt"));
    Files.writeString(
        results.resolve("attempt-2-result.json"),
        result("attempt-2", "failed", "failure.txt", "REST attempt"));
    Files.writeString(
        results.resolve("suite-container.json"),
        "{\"uuid\":\"suite\",\"children\":[\"attempt-1\",\"attempt-2\"],"
            + "\"befores\":[{\"name\":\"setup\",\"attachments\":[{\"name\":\"setup diagnostic\","
            + "\"source\":\"setup.txt\",\"type\":\"text/plain\"}]}]}");
    Path report = temporary.resolve("report.html");
    Files.writeString(report, standaloneReport());
    Path matrix = temporary.resolve("matrix.json");
    Files.writeString(matrix, matrix("REST attempt", 2));
    return new Fixture(results, report, matrix);
  }

  private static String result(String uuid, String status, String source, String step) {
    return "{\"uuid\":\""
        + uuid
        + "\",\"name\":\"REST evidence\",\"status\":\""
        + status
        + "\",\"steps\":[{\"name\":\""
        + step
        + "\",\"attachments\":[{\"name\":\"exchange\",\"source\":\""
        + source
        + "\",\"type\":\"text/plain\"}]}]}";
  }

  private static String container(String uuid, String child) {
    return "{\"uuid\":\"" + uuid + "\",\"children\":[\"" + child + "\"]}";
  }

  private static String matrix(String ownerStep, int count) {
    return """
        {
          "schemaVersion": "1.0",
          "expectedResultCount": 2,
          "requiredFields": ["sessionId", "testId", "stepId", "attempt", "sha256"],
          "forbiddenPatterns": ["secret-canary"],
          "standaloneRequiredMarkers": ["exchange"],
          "expectedResults": [
            {"name":"REST evidence","status":"passed","count":1,
             "attachments":[{"name":"exchange","count":1,"contentType":"text/plain","ownerStep":"%s","nonEmpty":true,"maxBytes":1024}]},
            {"name":"REST evidence","status":"failed","count":1,
             "attachments":[{"name":"exchange","count":1,"contentType":"text/plain","ownerStep":"%s","nonEmpty":true,"maxBytes":1024}]}
          ]
        }
        """
        .formatted(ownerStep, ownerStep);
  }

  private static String standaloneReport() {
    String passed =
        "{\"name\":\"REST evidence\",\"status\":\"passed\",\"testStage\":{"
            + "\"attachments\":[{\"name\":\"exchange\",\"source\":\"embedded-request\","
            + "\"type\":\"text/plain\"}],\"steps\":[]}}";
    String failed =
        "{\"name\":\"REST evidence\",\"status\":\"failed\",\"testStage\":{"
            + "\"attachments\":[{\"name\":\"exchange\",\"source\":\"embedded-failure\","
            + "\"type\":\"text/plain\"}],\"steps\":[]}}";
    return "<html><script>"
        + embedded("data/test-cases/passed.json", passed)
        + embedded("data/test-cases/failed.json", failed)
        + embedded(
            "data/attachments/embedded-request",
            "method=GET\nstatus=200\nsessionId=s1\ntestId=t1\nstepId=op1\nattempt=1\nsha256=abc")
        + embedded(
            "data/attachments/embedded-failure",
            "phase=connection\nsessionId=s1\ntestId=t1\nstepId=op2\nattempt=2\nsha256=def")
        + "</script></html>";
  }

  private static String embedded(String name, String value) {
    return "d('"
        + name
        + "','"
        + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8))
        + "');";
  }

  private record Fixture(Path results, Path report, Path matrix) {}

  @FunctionalInterface
  private interface Mutation {
    void apply(Fixture fixture) throws Exception;
  }
}

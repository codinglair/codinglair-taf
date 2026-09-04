package com.codinglair.taf.runtime.core.failure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.core.Error.ErrorType;
import java.util.List;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class FailurePolicyTest {
  private final FailureClassificationService classifications = new FailureClassificationService();
  private final FailureSignatureService signatures = new FailureSignatureService();

  @Test
  void explicitClassificationWinsAndContradictionsFail() {
    var explicit = new FailureContext("web", "action", FailureContext.Boundary.UNKNOWN,
        new AssertionError("mismatch"), List.of(ErrorType.PRODUCT_DEFECT));
    assertThat(classifications.classify(explicit).type()).isEqualTo(ErrorType.PRODUCT_DEFECT);
    assertThatThrownBy(() -> classifications.classify(new FailureContext("web", "action",
        FailureContext.Boundary.PRODUCT, new AssertionError(),
        List.of(ErrorType.PRODUCT_DEFECT, ErrorType.AUTOMATION_FAILURE))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void assertionAloneFallsBackToInconclusiveAndBoundaryRulesAreDeterministic() {
    assertThat(classifications.classify(FailureContext.of("web", "assert", FailureContext.Boundary.UNKNOWN,
        new AssertionError("expected x"))).type()).isEqualTo(ErrorType.INCONCLUSIVE);
    assertThat(classifications.classify(FailureContext.of("env", "setup", FailureContext.Boundary.ENVIRONMENT,
        new RuntimeException())).type()).isEqualTo(ErrorType.ENVIRONMENT_ISSUE);
    assertThat(classifications.classify(FailureContext.of("runner", "init", FailureContext.Boundary.AUTOMATION,
        new RuntimeException())).type()).isEqualTo(ErrorType.AUTOMATION_FAILURE);
    assertThat(classifications.classify(FailureContext.of("definitions", "resolve", FailureContext.Boundary.TEST_DATA,
        new RuntimeException())).type()).isEqualTo(ErrorType.TEST_DATA_ISSUE);
    assertThat(classifications.classify(FailureContext.of("requirements", "interpret", FailureContext.Boundary.REQUIREMENT,
        new RuntimeException())).type()).isEqualTo(ErrorType.REQUIREMENT_AMBIGUITY);
  }

  @Test
  void volatileValuesAndReclassificationDoNotChangeSignature() {
    RuntimeException first = new RuntimeException("failed at 2026-08-20T10:11:12Z id=123e4567-e89b-12d3-a456-426614174000 port:12345 password=hunter2");
    RuntimeException second = new RuntimeException("failed at 2027-01-01T01:02:03Z id=223e4567-e89b-12d3-a456-426614174999 port:54321 password=different");
    first.setStackTrace(new StackTraceElement[] {new StackTraceElement("Example", "run", "Example.java", 10)});
    second.setStackTrace(new StackTraceElement[] {new StackTraceElement("Example", "run", "Example.java", 99)});
    var one = new FailureContext("web", "action", FailureContext.Boundary.PRODUCT, first, List.of());
    var two = new FailureContext("web", "action", FailureContext.Boundary.AUTOMATION, second, List.of());
    assertThat(signatures.sign(one)).isEqualTo(signatures.sign(two));
    assertThat(signatures.sign(one).value()).matches("failure-signature:v1:[0-9a-f]{64}");
    assertThat(signatures.sign(FailureContext.of("web", "action", FailureContext.Boundary.UNKNOWN,
        new IllegalStateException("materially different")))).isNotEqualTo(signatures.sign(one));
  }

  @Test
  void boundsCauseChainsAndExcludesSensitiveOrRawEvidenceInputs() {
    var bounded = new FailureSignatureService(new com.codinglair.taf.runtime.core.reporting.RedactionPipeline(), 24, 1, 2);
    RuntimeException first = new RuntimeException("credential=canary-secret " + "x".repeat(100),
        new IllegalStateException("stable cause", new IllegalArgumentException("ignored tail one")));
    RuntimeException second = new RuntimeException("credential=other-secret " + "x".repeat(100),
        new IllegalStateException("stable cause", new IllegalArgumentException("ignored tail two")));
    first.setStackTrace(new StackTraceElement[] {new StackTraceElement("A", "one", "A.java", 1), new StackTraceElement("Ignored", "x", "I.java", 2)});
    second.setStackTrace(new StackTraceElement[] {new StackTraceElement("A", "one", "A.java", 99), new StackTraceElement("DifferentIgnored", "y", "D.java", 3)});
    var one = FailureContext.of("web", "action", FailureContext.Boundary.UNKNOWN, first);
    var two = FailureContext.of("web", "action", FailureContext.Boundary.UNKNOWN, second);
    assertThat(bounded.sign(one)).isEqualTo(bounded.sign(two));
    assertThat(bounded.canonicalForTesting(one)).doesNotContain("canary-secret", "ignored tail", "Ignored", "request body", "payload");
  }

  @Test
  void signatureIsStableAcrossRepeatedJava25ProcessesAndPathForms() throws Exception {
    String first = probe("failed /tmp/run-123/file container-api-deadbeefcafebabe at 2026-08-20T10:11:12Z", "10");
    String second = probe("failed C:\\work\\temp\\run-999\\file container-api-0123456789abcdef at 2027-01-01T01:02:03Z", "99");
    assertThat(first).isEqualTo(second).matches("failure-signature:v1:[0-9a-f]{64}");
  }

  private static String probe(String message, String line) throws Exception {
    Path executable = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
    Process process = new ProcessBuilder(executable.toString(), "-cp", System.getProperty("java.class.path"),
        FailureSignatureProcessProbe.class.getName(), message, line).start();
    assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
    String diagnostics = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(process.exitValue()).as("stdout: %s%n stderr: %s", output, diagnostics).isZero();
    return output;
  }

  @Test
  void legacyFlakyEnumRemainsAvailableButDoesNotReplaceIndependentStability() {
    assertThat(ErrorType.valueOf("FLAKY")).isEqualTo(ErrorType.FLAKY);
    var analysis = FailureAnalysis.classify("web", "action", FailureContext.Boundary.PRODUCT,
        new RuntimeException("failure"));
    assertThat(analysis.classification().type()).isEqualTo(ErrorType.PRODUCT_DEFECT);
    assertThat(analysis.stability()).isEqualTo(StabilityStatus.INSUFFICIENT_HISTORY);
  }
}

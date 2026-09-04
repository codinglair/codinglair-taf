package com.codinglair.taf.runtime.core.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.core.Error.ErrorType;
import com.codinglair.taf.runtime.core.failure.FailureClassification;
import com.codinglair.taf.runtime.core.failure.FailureSignature;
import com.codinglair.taf.runtime.core.failure.StabilityStatus;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileExecutionHistoryRepositoryTest {
  @TempDir Path root;

  @Test
  void recordsQueriesAndTreatsDuplicateAttemptIdempotently() {
    var repository = repository(20);
    var record = summary("attempt-1");
    assertThat(repository.record(record).status()).isEqualTo(HistoryResult.Status.SUCCESS);
    assertThat(repository.record(record).diagnostic()).contains("duplicate");
    var collision = new ExecutionAttemptSummary(1, record.projectId(), record.testId(),
        record.executionId(), record.attemptId(), record.completedAt(),
        ExecutionAttemptSummary.Outcome.PASSED, null, StabilityStatus.STABLE, null,
        record.duration(), record.buildId(), record.environmentId());
    assertThat(repository.record(collision).status()).isEqualTo(HistoryResult.Status.CORRUPT);
    assertThat(repository.findByTest("project", "test", 10, Duration.ofDays(1)).records()).containsExactly(record);
    assertThat(repository.findBySignature("project", record.signature().value(), 10, Duration.ofDays(1)).records()).containsExactly(record);
  }

  @Test
  void concurrentPublicationNeverExposesPartialRecords() throws Exception {
    var repository = repository(100);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int index = 0; index < 32; index++) {
        int attempt = index;
        executor.submit(() -> repository.record(summary("attempt-" + attempt)));
      }
    }
    var result = repository.findByTest("project", "test", 100, Duration.ofDays(1));
    assertThat(result.status()).isEqualTo(HistoryResult.Status.SUCCESS);
    assertThat(result.records()).hasSize(32);
    assertThat(result.records()).extracting(ExecutionAttemptSummary::projectId).containsOnly("project");
    assertThat(result.records()).extracting(ExecutionAttemptSummary::testId).containsOnly("test");
    assertThat(result.records()).extracting(ExecutionAttemptSummary::attemptId).doesNotHaveDuplicates();
  }

  @Test
  void corruptionIsExplicitAndUnsafeIdentitiesAreRejected() throws Exception {
    var repository = repository(20);
    repository.record(summary("attempt-1"));
    Path json = Files.walk(root).filter(path -> path.toString().endsWith(".json")).findFirst().orElseThrow();
    Files.writeString(json, "{broken");
    assertThat(repository.findByTest("project", "test", 10, Duration.ofDays(1)).status()).isEqualTo(HistoryResult.Status.CORRUPT);
    assertThatThrownBy(() -> repository.findByTest("../escape", "test", 10, Duration.ofDays(1))).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void disabledIsDistinctFromSuccessfulEmptyHistoryAndRetentionIsBounded() {
    var disabled = new DisabledExecutionHistoryRepository();
    assertThat(disabled.findByTest("project", "test", 1, Duration.ofDays(1)).status()).isEqualTo(HistoryResult.Status.DISABLED);
    var repository = repository(2);
    repository.record(summary("attempt-1"));
    repository.record(summary("attempt-2"));
    repository.record(summary("attempt-3"));
    assertThat(repository.findByTest("project", "test", 10, Duration.ofDays(1)).records()).hasSize(2);
  }

  @Test
  void isolatesProjectsTestsAndRerunAttempts() {
    var repository = repository(20);
    repository.record(summary("attempt-1"));
    repository.record(new ExecutionAttemptSummary(1, "other", "test", "execution", "attempt-1", Instant.now(),
        ExecutionAttemptSummary.Outcome.PASSED, null, StabilityStatus.INSUFFICIENT_HISTORY, null,
        Duration.ZERO, "build", "local"));
    assertThat(repository.findByTest("project", "test", 10, Duration.ofDays(1)).records())
        .extracting(ExecutionAttemptSummary::attemptId).containsExactly("attempt-1");
    assertThat(repository.findByTest("other", "test", 10, Duration.ofDays(1)).records()).hasSize(1);
  }

  @Test
  void parallelJavaProcessesPublishCompleteSeparateAttempts() throws Exception {
    var processes = new ArrayList<Process>();
    Path java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
    for (int index = 0; index < 8; index++) {
      processes.add(new ProcessBuilder(java.toString(), "-cp", System.getProperty("java.class.path"),
          FileHistoryProcessProbe.class.getName(), root.toString(), "process-" + index).redirectErrorStream(true).start());
    }
    for (Process process : processes) {
      assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      assertThat(process.exitValue()).as(output).isZero();
    }
    assertThat(repository(100).findByTest("project", "test", 100, Duration.ofDays(1)).records()).hasSize(8);
    assertThat(Files.walk(root).filter(path -> path.toString().endsWith(".tmp"))).isEmpty();
  }

  @Test
  void unavailableAndPartiallyWrittenAreDistinctAndDoNotMutateCurrentSummary() throws Exception {
    Path unavailableRoot = root.resolve("not-a-directory");
    Files.writeString(unavailableRoot, "occupied");
    var configuration = new HistoryConfiguration(true, unavailableRoot, 10, Duration.ofDays(1), 10_000,
        HistoryConfiguration.UnavailabilityPolicy.CONTINUE, HistoryConfiguration.CorruptionPolicy.REPORT);
    var current = summary("authoritative");
    assertThat(new FileExecutionHistoryRepository(configuration).record(current).status()).isEqualTo(HistoryResult.Status.UNAVAILABLE);
    assertThat(current.classification().type()).isEqualTo(ErrorType.AUTOMATION_FAILURE);

    var repository = repository(20);
    repository.record(summary("partial"));
    Path json = Files.walk(root).filter(path -> path.toString().endsWith(".json")).findFirst().orElseThrow();
    Files.writeString(json, "{\"schemaVersion\":1");
    assertThat(repository.findByTest("project", "test", 10, Duration.ofDays(1)).status()).isEqualTo(HistoryResult.Status.CORRUPT);
  }

  @Test
  void rejectsSymlinkEscapeWithoutTouchingOutsideTarget() throws Exception {
    Path outside = root.resolveSibling(root.getFileName() + "-outside");
    Files.createDirectories(root);
    Files.createDirectories(outside);
    Path projectLink = root.resolve(sha256("project"));
    try {
      Files.createSymbolicLink(projectLink, outside);
    } catch (UnsupportedOperationException | java.nio.file.FileSystemException denied) {
      Assumptions.abort("Symbolic links are unavailable for this Windows execution identity");
    }
    assertThat(repository(20).record(summary("escape")).status()).isEqualTo(HistoryResult.Status.UNAVAILABLE);
    assertThat(Files.list(outside)).isEmpty();
  }

  @Test
  void rejectsAbsoluteAndTraversalIdentities() {
    var repository = repository(20);
    assertThatThrownBy(() -> repository.findByTest("C:\\absolute", "test", 1, Duration.ofDays(1))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> repository.findByTest("project", "../test", 1, Duration.ofDays(1))).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void retentionEnforcesAgeAndByteBoundsDeterministically() throws Exception {
    var ageConfiguration = new HistoryConfiguration(true, root, 20, Duration.ofDays(1), 1_000_000,
        HistoryConfiguration.UnavailabilityPolicy.CONTINUE, HistoryConfiguration.CorruptionPolicy.REPORT);
    var repository = new FileExecutionHistoryRepository(ageConfiguration);
    repository.record(summary("old"));
    Path old = Files.walk(root).filter(path -> path.toString().endsWith(".json")).findFirst().orElseThrow();
    Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(Duration.ofDays(2))));
    repository.record(summary("new"));
    assertThat(repository.findByTest("project", "test", 20, Duration.ofDays(30)).records())
        .extracting(ExecutionAttemptSummary::attemptId).containsExactly("new");

    Path byteRoot = root.resolve("bytes");
    var byteRepository = new FileExecutionHistoryRepository(new HistoryConfiguration(true, byteRoot, 20,
        Duration.ofDays(1), 1024, HistoryConfiguration.UnavailabilityPolicy.CONTINUE,
        HistoryConfiguration.CorruptionPolicy.REPORT));
    byteRepository.record(summary("byte-1"));
    byteRepository.record(summary("byte-2"));
    byteRepository.record(summary("byte-3"));
    assertThat(Files.walk(byteRoot).filter(path -> path.toString().endsWith(".json"))
        .mapToLong(path -> { try { return Files.size(path); } catch (Exception failure) { throw new RuntimeException(failure); } }).sum())
        .isLessThanOrEqualTo(1024);
  }

  @Test
  void quarantineMovesCorruptAndUnknownFutureRecordsOutOfTheValidView() throws Exception {
    var configuration = new HistoryConfiguration(true, root, 20, Duration.ofDays(1), 1_000_000,
        HistoryConfiguration.UnavailabilityPolicy.CONTINUE, HistoryConfiguration.CorruptionPolicy.QUARANTINE);
    var repository = new FileExecutionHistoryRepository(configuration);
    repository.record(summary("future"));
    Path json = Files.walk(root).filter(path -> path.toString().endsWith(".json")).findFirst().orElseThrow();
    Files.writeString(json, Files.readString(json).replace("AUTOMATION_FAILURE", "FUTURE_CLASSIFICATION"));
    assertThat(repository.findByTest("project", "test", 20, Duration.ofDays(1)).status())
        .isEqualTo(HistoryResult.Status.CORRUPT);
    assertThat(Files.exists(json)).isFalse();
    assertThat(Files.list(root.resolve("quarantine"))).isNotEmpty();
  }

  @Test
  void historyJsonContainsOnlyBoundedMetadataAndNoRawSecretOrEvidence() throws Exception {
    var repository = repository(20);
    var sensitive = new ExecutionAttemptSummary(1, "project", "test", "execution", "secret-attempt",
        Instant.now(), ExecutionAttemptSummary.Outcome.FAILED,
        new FailureClassification(ErrorType.AUTOMATION_FAILURE, FailureClassification.Source.RUNTIME_RULE,
            "token=history-canary"), StabilityStatus.INSUFFICIENT_HISTORY,
        new FailureSignature("failure-signature:v1:" + "d".repeat(64), "v1"), Duration.ofMillis(1),
        "build", "local");
    repository.record(sensitive);
    String stored = Files.readString(Files.walk(root).filter(path -> path.toString().endsWith(".json"))
        .filter(path -> { try { return Files.readString(path).contains("secret-attempt"); } catch (Exception failure) { return false; } })
        .findFirst().orElseThrow());
    assertThat(stored).doesNotContain("history-canary", "stackTrace", "request", "response", "payload",
        "screenshot", "expectedOutput", "actualOutput");
    assertThat(stored.length()).isLessThan(4096);
  }

  private static String sha256(String value) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  private FileExecutionHistoryRepository repository(int maximumRecords) {
    return new FileExecutionHistoryRepository(new HistoryConfiguration(true, root, maximumRecords,
        Duration.ofDays(30), 1_000_000, HistoryConfiguration.UnavailabilityPolicy.CONTINUE,
        HistoryConfiguration.CorruptionPolicy.REPORT));
  }

  private ExecutionAttemptSummary summary(String attempt) {
    return new ExecutionAttemptSummary(1, "project", "test", "execution", attempt, Instant.now(),
        ExecutionAttemptSummary.Outcome.FAILED,
        new FailureClassification(ErrorType.AUTOMATION_FAILURE, FailureClassification.Source.RUNTIME_RULE, "runner boundary"),
        StabilityStatus.INSUFFICIENT_HISTORY,
        new FailureSignature("failure-signature:v1:" + "b".repeat(64), "v1"), Duration.ofMillis(5), "build", "local");
  }
}

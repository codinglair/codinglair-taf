package com.codinglair.taf.runtime.core.history;

import com.codinglair.taf.core.Error.ErrorType;
import com.codinglair.taf.runtime.core.failure.FailureClassification;
import com.codinglair.taf.runtime.core.failure.FailureSignature;
import com.codinglair.taf.runtime.core.failure.StabilityStatus;
import com.codinglair.taf.runtime.core.reporting.RedactionPipeline;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable JSON-per-attempt provider with atomic publication and project isolation. */
public final class FileExecutionHistoryRepository implements ExecutionHistoryRepository {
  private static final Pattern FIELD = Pattern.compile("\\\"([^\\\"]+)\\\":(?:\\\"((?:\\\\.|[^\\\"])*)\\\"|(-?\\d+)|null)");
  private final HistoryConfiguration configuration;
  private final RedactionPipeline redaction = new RedactionPipeline();

  public FileExecutionHistoryRepository(HistoryConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override
  public HistoryResult record(ExecutionAttemptSummary summary) {
    if (!configuration.enabled()) return disabled();
    Path directory = identityDirectory(summary.projectId(), summary.testId());
    Path target = directory.resolve(hash(summary.executionId() + "\u0000" + summary.attemptId()) + ".json");
    try {
      createSecureDirectories(directory);
      String encoded = encode(summary);
      if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return duplicateResult(target, encoded, summary);
      Path temporary = directory.resolve("." + UUID.randomUUID() + ".tmp");
      Files.writeString(temporary, encoded, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
      try {
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException unsupported) {
        Files.deleteIfExists(temporary);
        throw new IOException("atomic history publication is not supported", unsupported);
      } catch (java.nio.file.FileAlreadyExistsException duplicate) {
        Files.deleteIfExists(temporary);
        return duplicateResult(target, encoded, summary);
      }
      retain(directory);
      return success(List.of(summary), "recorded");
    } catch (IOException failure) {
      return unavailable(failure);
    }
  }

  private HistoryResult duplicateResult(Path target, String encoded,
      ExecutionAttemptSummary summary) throws IOException {
    if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
        && Files.readString(target, StandardCharsets.UTF_8).equals(encoded)) {
      return success(List.of(summary), "idempotent duplicate ignored");
    }
    return new HistoryResult(HistoryResult.Status.CORRUPT, List.of(),
        "attempt identity collision");
  }

  @Override
  public HistoryResult findByTest(String projectId, String testId, int maximumRecords, Duration maximumAge) {
    validateWindow(maximumRecords, maximumAge);
    if (!configuration.enabled()) return disabled();
    Path directory = identityDirectory(projectId, testId);
    if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return success(List.of(), "empty");
    try {
      return read(directory, Math.min(maximumRecords, configuration.maximumRecords()),
          shorter(maximumAge, configuration.maximumAge()), null);
    } catch (IOException failure) {
      return unavailable(failure);
    }
  }

  @Override
  public HistoryResult findBySignature(String projectId, String signature, int maximumRecords, Duration maximumAge) {
    validateWindow(maximumRecords, maximumAge);
    if (!configuration.enabled()) return disabled();
    Path project = configuration.root().resolve(hash(safe(projectId))).normalize();
    if (!project.startsWith(configuration.root()) || !Files.isDirectory(project, LinkOption.NOFOLLOW_LINKS)) return success(List.of(), "empty");
    try {
      return read(project, Math.min(maximumRecords, configuration.maximumRecords()),
          shorter(maximumAge, configuration.maximumAge()), signature);
    } catch (IOException failure) {
      return unavailable(failure);
    }
  }

  private HistoryResult read(Path root, int maximumRecords, Duration maximumAge, String signature) throws IOException {
    Instant cutoff = Instant.now().minus(maximumAge);
    List<ExecutionAttemptSummary> records = new ArrayList<>();
    boolean corrupt = false;
    long bytesRead = 0;
    try (var paths = Files.walk(root)) {
      List<Path> candidates = paths.filter(item -> Files.isRegularFile(item, LinkOption.NOFOLLOW_LINKS)
          && item.getFileName().toString().endsWith(".json"))
          .limit((long) configuration.maximumRecords() * 4 + 1).toList();
      if (candidates.size() > (long) configuration.maximumRecords() * 4) corrupt = true;
      for (Path path : candidates.stream().limit((long) configuration.maximumRecords() * 4).toList()) {
        try {
          long size = Files.size(path);
          bytesRead += size;
          if (size > configuration.maximumBytes() || bytesRead > configuration.maximumBytes()) {
            corrupt = true;
            continue;
          }
          ExecutionAttemptSummary item = decode(Files.readString(path, StandardCharsets.UTF_8));
          if (!item.completedAt().isBefore(cutoff) && (signature == null || item.signature() != null && item.signature().value().equals(signature))) records.add(item);
        } catch (RuntimeException malformed) {
          corrupt = true;
          quarantine(path);
        }
      }
    }
    records.sort(Comparator.comparing(ExecutionAttemptSummary::completedAt).reversed().thenComparing(ExecutionAttemptSummary::attemptId));
    List<ExecutionAttemptSummary> bounded = records.stream().limit(maximumRecords).toList();
    return new HistoryResult(corrupt ? HistoryResult.Status.CORRUPT : HistoryResult.Status.SUCCESS, bounded,
        corrupt ? "one or more corrupt records excluded" : "ok");
  }

  private void retain(Path directory) throws IOException {
    List<Path> records;
    try (var paths = Files.list(directory)) {
      records = paths.filter(item -> Files.isRegularFile(item, LinkOption.NOFOLLOW_LINKS) && item.getFileName().toString().endsWith(".json"))
          .sorted(Comparator.comparingLong(this::modified).reversed().thenComparing(Path::toString)).toList();
    }
    long bytes = 0;
    Instant cutoff = Instant.now().minus(configuration.maximumAge());
    for (int index = 0; index < records.size(); index++) {
      Path path = records.get(index);
      long size = Files.size(path);
      bytes += size;
      if (index >= configuration.maximumRecords() || bytes > configuration.maximumBytes() || Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff)) Files.delete(path);
    }
  }

  private void quarantine(Path path) throws IOException {
    if (configuration.corruptionPolicy() != HistoryConfiguration.CorruptionPolicy.QUARANTINE) return;
    Path quarantine = configuration.root().resolve("quarantine");
    Files.createDirectories(quarantine);
    Files.move(path, quarantine.resolve(path.getFileName() + ".corrupt-" + UUID.randomUUID()), StandardCopyOption.ATOMIC_MOVE);
  }

  private Path identityDirectory(String project, String test) {
    Path result = configuration.root().resolve(hash(safe(project))).resolve(hash(safe(test))).normalize();
    if (!result.startsWith(configuration.root())) throw new IllegalArgumentException("history path escapes configured root");
    return result;
  }

  private void createSecureDirectories(Path directory) throws IOException {
    Path current = configuration.root();
    if (Files.isSymbolicLink(current)) throw new IOException("history root must not be a symbolic link");
    Files.createDirectories(current);
    Path relative = configuration.root().relativize(directory);
    for (Path part : relative) {
      current = current.resolve(part);
      if (Files.isSymbolicLink(current)) throw new IOException("symbolic links are forbidden in history paths");
      try {
        Files.createDirectory(current);
      } catch (java.nio.file.FileAlreadyExistsException existing) {
        if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) throw new IOException("history path is not a directory");
      }
    }
  }

  private String encode(ExecutionAttemptSummary s) {
    return "{\"schemaVersion\":1,\"projectId\":\"" + esc(s.projectId()) + "\",\"testId\":\"" + esc(s.testId()) + "\",\"executionId\":\"" + esc(s.executionId()) + "\",\"attemptId\":\"" + esc(s.attemptId()) + "\",\"completedAt\":\"" + s.completedAt() + "\",\"outcome\":\"" + s.outcome() + "\",\"classification\":" + quote(s.classification() == null ? null : s.classification().type().name()) + ",\"classificationSource\":" + quote(s.classification() == null ? null : s.classification().source().name()) + ",\"classificationReason\":" + quote(s.classification() == null ? null : redaction.redact(s.classification().reason())) + ",\"stability\":\"" + s.stability() + "\",\"signature\":" + quote(s.signature() == null ? null : s.signature().value()) + ",\"signatureAlgorithm\":" + quote(s.signature() == null ? null : s.signature().algorithm()) + ",\"durationMillis\":" + s.duration().toMillis() + ",\"buildId\":\"" + esc(s.buildId()) + "\",\"environmentId\":\"" + esc(s.environmentId()) + "\"}";
  }

  private ExecutionAttemptSummary decode(String json) {
    Map<String, String> values = new java.util.HashMap<>();
    Matcher matcher = FIELD.matcher(json);
    while (matcher.find()) values.put(matcher.group(1), matcher.group(2) != null ? unesc(matcher.group(2)) : matcher.group(3));
    if (values.size() < 10 || Integer.parseInt(values.get("schemaVersion")) != 1) throw new IllegalArgumentException("invalid history JSON");
    FailureClassification classification = values.get("classification") == null ? null : new FailureClassification(ErrorType.valueOf(values.get("classification")), FailureClassification.Source.valueOf(values.get("classificationSource")), values.get("classificationReason"));
    FailureSignature signature = values.get("signature") == null ? null : new FailureSignature(values.get("signature"), values.get("signatureAlgorithm"));
    return new ExecutionAttemptSummary(1, values.get("projectId"), values.get("testId"), values.get("executionId"), values.get("attemptId"), Instant.parse(values.get("completedAt")), ExecutionAttemptSummary.Outcome.valueOf(values.get("outcome")), classification, StabilityStatus.valueOf(values.get("stability")), signature, Duration.ofMillis(Long.parseLong(values.get("durationMillis"))), values.get("buildId"), values.get("environmentId"));
  }

  private static String safe(String value) { if (value == null || !value.matches("[A-Za-z0-9._-]{1,160}")) throw new IllegalArgumentException("unsafe history identity"); return value; }
  private static String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); } }
  private static String esc(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
  private static String unesc(String value) { return value.replace("\\\"", "\"").replace("\\\\", "\\"); }
  private static String quote(String value) { return value == null ? "null" : "\"" + esc(value) + "\""; }
  private long modified(Path path) { try { return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis(); } catch (IOException failure) { return Long.MIN_VALUE; } }
  private static void validateWindow(int maximumRecords, Duration age) { if (maximumRecords < 1 || age == null || age.isNegative() || age.isZero()) throw new IllegalArgumentException("invalid history query window"); }
  private static Duration shorter(Duration first, Duration second) { return first.compareTo(second) <= 0 ? first : second; }
  private static HistoryResult success(List<ExecutionAttemptSummary> records, String message) { return new HistoryResult(HistoryResult.Status.SUCCESS, records, message); }
  private static HistoryResult disabled() { return new HistoryResult(HistoryResult.Status.DISABLED, List.of(), "history disabled"); }
  private HistoryResult unavailable(Exception failure) { if (configuration.unavailabilityPolicy() == HistoryConfiguration.UnavailabilityPolicy.REQUIRE_HISTORY) throw new IllegalStateException("Required history unavailable", failure); return new HistoryResult(HistoryResult.Status.UNAVAILABLE, List.of(), "history unavailable: " + failure.getClass().getSimpleName()); }
}

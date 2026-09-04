package com.codinglair.taf.runtime.core.reporting.impl.allure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/** Publishes finalized, sanitized Allure results as one collision-safe offline HTML artifact. */
public final class AllureSingleFilePublisher {
  private static final System.Logger LOGGER =
      System.getLogger(AllureSingleFilePublisher.class.getName());
  private static final Pattern INVALID_REPORT_NAME = Pattern.compile("[<>:\"/\\\\|?*\\p{Cntrl}]");
  private static final Pattern WINDOWS_RESERVED =
      Pattern.compile("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?");

  private final AllureSingleFileProperties properties;
  private final SingleFileReportGenerator generator;
  private final Clock clock;
  private final DateTimeFormatter timestampFormatter;
  private final String reportName;
  private final AtomicReference<CompletableFuture<Optional<Path>>> publication =
      new AtomicReference<>();

  AllureSingleFilePublisher(
      AllureSingleFileProperties properties, SingleFileReportGenerator generator, Clock clock) {
    this.properties = Objects.requireNonNull(properties, "properties");
    this.generator = Objects.requireNonNull(generator, "generator");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.reportName = validate(properties);
    this.timestampFormatter = timestampFormatter(properties.getTimestampPattern());
    validateTimestampSegment(timestampFormatter.format(LocalDateTime.of(2001, 11, 23, 21, 45)));
  }

  /** Publishes at most once for this logical run, including concurrent duplicate callbacks. */
  public Optional<Path> publishOnce() {
    CompletableFuture<Optional<Path>> candidate = new CompletableFuture<>();
    CompletableFuture<Optional<Path>> existing = publication.compareAndExchange(null, candidate);
    if (existing != null) {
      return existing.join();
    }
    try {
      Optional<Path> artifact = properties.isEnabled() ? Optional.of(publish()) : Optional.empty();
      candidate.complete(artifact);
      return artifact;
    } catch (Throwable failure) {
      candidate.completeExceptionally(failure);
      throw failure;
    }
  }

  /** Returns the successfully published artifact, if publication has completed. */
  public Optional<Path> publishedArtifact() {
    CompletableFuture<Optional<Path>> current = publication.get();
    if (current == null || !current.isDone() || current.isCompletedExceptionally()) {
      return Optional.empty();
    }
    return current.join();
  }

  String configurationKey() {
    return properties.isEnabled()
        + "|"
        + absolute(properties.getResultsDirectory())
        + "|"
        + absolute(properties.getOutputDirectory())
        + "|"
        + reportName
        + "|"
        + properties.getTimestampPattern()
        + "|"
        + properties.getExecutable()
        + "|"
        + properties.getTimeout();
  }

  private Path publish() {
    Path results = absolute(properties.getResultsDirectory());
    validateResults(results);
    Path root = absolute(properties.getOutputDirectory());
    String timestamp = timestampFormatter.format(LocalDateTime.now(clock));
    validateTimestampSegment(timestamp);

    Path targetDirectory = reserveTargetDirectory(root, timestamp);
    Path staging = null;
    Path generatorLog = null;
    try {
      staging = Files.createTempDirectory(root, ".taf-allure-single-file-");
      generatorLog = staging.resolveSibling(staging.getFileName() + ".log");
      Path generated =
          generator.generate(
              new SingleFileReportGenerator.GenerationRequest(
                  results, staging, properties.getExecutable(), properties.getTimeout()));
      validateGenerated(generated);
      Path target = targetDirectory.resolve(reportName + "_" + timestamp + ".html");
      moveWithoutOverwrite(generated, target);
      validateGenerated(target);
      Path resolved = target.toAbsolutePath().normalize();
      LOGGER.log(System.Logger.Level.INFO, "Published Allure single-file report: {0}", resolved);
      return resolved;
    } catch (AllureSingleFilePublicationException failure) {
      throw failure;
    } catch (IOException failure) {
      throw new AllureSingleFilePublicationException(
          "filesystem",
          "results source "
              + results
              + ", output target "
              + targetDirectory
              + "; verify permissions and free space",
          failure);
    } finally {
      deleteGeneratedPath(staging);
      deleteGeneratedPath(generatorLog);
      deleteIfEmpty(targetDirectory);
    }
  }

  private static String validate(AllureSingleFileProperties properties) {
    Objects.requireNonNull(properties.getOutputDirectory(), "output-directory must not be null");
    Objects.requireNonNull(properties.getResultsDirectory(), "results-directory must not be null");
    Objects.requireNonNull(properties.getTimeout(), "timeout must not be null");
    if (properties.getTimeout().isZero() || properties.getTimeout().isNegative()) {
      throw new IllegalArgumentException("timeout must be greater than zero");
    }
    if (properties.getExecutable() == null || properties.getExecutable().isBlank()) {
      throw new IllegalArgumentException("executable must not be blank");
    }
    String name = properties.getReportName() == null ? "" : properties.getReportName().trim();
    if (name.isBlank()
        || INVALID_REPORT_NAME.matcher(name).find()
        || name.equals(".")
        || name.equals("..")) {
      throw new IllegalArgumentException(
          "report-name must be non-blank and must not contain traversal or filesystem-invalid characters");
    }
    if (WINDOWS_RESERVED.matcher(name).matches() || name.endsWith(".") || name.endsWith(" ")) {
      throw new IllegalArgumentException(
          "report-name is reserved or invalid on supported filesystems");
    }
    return name;
  }

  private static DateTimeFormatter timestampFormatter(String pattern) {
    if (pattern == null || pattern.isBlank()) {
      throw new IllegalArgumentException("timestamp-pattern must not be blank");
    }
    try {
      return DateTimeFormatter.ofPattern(pattern, Locale.ROOT);
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException("invalid timestamp-pattern '" + pattern + "'", failure);
    }
  }

  private static void validateTimestampSegment(String value) {
    if (value.isBlank()
        || INVALID_REPORT_NAME.matcher(value).find()
        || value.equals(".")
        || value.equals("..")) {
      throw new IllegalArgumentException(
          "timestamp-pattern must produce one non-blank filesystem-safe directory segment");
    }
  }

  private static void validateResults(Path results) {
    if (!Files.isDirectory(results) || !Files.isReadable(results)) {
      throw new AllureSingleFilePublicationException(
          "results-preflight",
          "configured results source "
              + results
              + " is missing, not a readable directory, or not finalized; retain results and correct the path");
    }
    try (var files = Files.list(results)) {
      if (files.noneMatch(Files::isRegularFile)) {
        throw new AllureSingleFilePublicationException(
            "results-preflight",
            "configured results source " + results + " contains no result files");
      }
    } catch (IOException failure) {
      throw new AllureSingleFilePublicationException(
          "results-preflight", "could not inspect configured results source " + results, failure);
    }
  }

  private static Path reserveTargetDirectory(Path root, String timestamp) {
    try {
      Files.createDirectories(root);
      for (int collision = 1; ; collision++) {
        String directoryName = collision == 1 ? timestamp : timestamp + "-" + collision;
        Path candidate = root.resolve(directoryName);
        try {
          return Files.createDirectory(candidate);
        } catch (FileAlreadyExistsException ignored) {
          // A numeric suffix is deterministic and never overwrites the existing publication.
        }
      }
    } catch (IOException failure) {
      throw new AllureSingleFilePublicationException(
          "target-reservation", "could not create output root " + root, failure);
    }
  }

  private static void validateGenerated(Path artifact) {
    try {
      if (!Files.isRegularFile(artifact) || Files.size(artifact) == 0) {
        throw new AllureSingleFilePublicationException(
            "artifact-validation",
            "generator returned no regular non-empty HTML artifact at " + artifact);
      }
      boolean htmlElement = false;
      boolean scriptElement = false;
      boolean allureBootstrap = false;
      try (var reader = Files.newBufferedReader(artifact, StandardCharsets.UTF_8)) {
        String line;
        while ((line = reader.readLine()) != null
            && !(htmlElement && scriptElement && allureBootstrap)) {
          String normalized = line.toLowerCase(Locale.ROOT);
          htmlElement |= normalized.contains("<html");
          scriptElement |= normalized.contains("<script");
          allureBootstrap |= normalized.contains("allure");
        }
      }
      if (!htmlElement || !scriptElement || !allureBootstrap) {
        throw new AllureSingleFilePublicationException(
            "artifact-validation",
            "artifact at " + artifact + " lacks the expected HTML and Allure bootstrap structure");
      }
    } catch (IOException failure) {
      throw new AllureSingleFilePublicationException(
          "artifact-validation", "could not inspect generated artifact " + artifact, failure);
    }
  }

  private static void moveWithoutOverwrite(Path source, Path target) throws IOException {
    try {
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException ignored) {
      Files.move(source, target);
    }
  }

  private static Path absolute(Path path) {
    return path.toAbsolutePath().normalize();
  }

  private static void deleteGeneratedPath(Path path) {
    if (path == null || !Files.exists(path)) {
      return;
    }
    try (var paths = Files.walk(path)) {
      paths.sorted(Comparator.reverseOrder()).forEach(AllureSingleFilePublisher::deleteQuietly);
    } catch (IOException ignored) {
      // Cleanup failure must not hide the publication result or primary actionable failure.
    }
  }

  private static void deleteIfEmpty(Path directory) {
    if (directory == null || !Files.isDirectory(directory)) {
      return;
    }
    try (var children = Files.list(directory)) {
      if (children.findAny().isEmpty()) {
        Files.deleteIfExists(directory);
      }
    } catch (IOException ignored) {
      // Empty reservation cleanup is best effort; no source results or valid report are removed.
    }
  }

  private static void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // Best-effort cleanup of adapter-owned staging only.
    }
  }
}

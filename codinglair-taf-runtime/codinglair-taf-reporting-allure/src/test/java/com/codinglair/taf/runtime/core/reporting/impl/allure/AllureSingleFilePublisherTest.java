package com.codinglair.taf.runtime.core.reporting.impl.allure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AllureSingleFilePublisherTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-10T02:45:00Z"), ZoneOffset.UTC);
  @TempDir Path temporary;

  @Test
  void disabledPublicationCreatesNothing() {
    AllureSingleFileProperties properties = properties();
    properties.setEnabled(false);
    Path output = properties.getOutputDirectory();

    assertThat(publisher(properties, validGenerator(new AtomicInteger())).publishOnce()).isEmpty();

    assertThat(output).doesNotExist();
  }

  @Test
  void defaultTimestampAndNameWithSpacesProduceExpectedArtifact() throws IOException {
    AllureSingleFileProperties properties = properties();
    properties.setReportName("SauceDemo Functional Tests");

    Path artifact =
        publisher(properties, validGenerator(new AtomicInteger())).publishOnce().orElseThrow();

    assertThat(artifact)
        .isEqualTo(
            temporary
                .resolve("reports/202608100245/SauceDemo Functional Tests_202608100245.html")
                .toAbsolutePath());
    assertThat(artifact).isNotEmptyFile();
    assertThat(Files.list(artifact.getParent()).map(Path::getFileName))
        .containsExactly(artifact.getFileName());
    assertThat(properties.getResultsDirectory().resolve("source-result.json")).exists();
  }

  @Test
  void customOutputAndTimestampPatternAreUsed() {
    AllureSingleFileProperties properties = properties();
    properties.setOutputDirectory(temporary.resolve("custom-output"));
    properties.setTimestampPattern("yyyy-MM-dd_HHmmss");

    Path artifact =
        publisher(properties, validGenerator(new AtomicInteger())).publishOnce().orElseThrow();

    assertThat(artifact.getParent().getFileName()).hasToString("2026-08-10_024500");
    assertThat(artifact.getFileName()).hasToString("TAF Test Report_2026-08-10_024500.html");
  }

  @ParameterizedTest
  @ValueSource(strings = {"../escape", "..\\escape", "report:name", " ", "CON", "report*"})
  void unsafeReportNamesFailAtPreflight(String reportName) {
    AllureSingleFileProperties properties = properties();
    properties.setReportName(reportName);

    assertThatThrownBy(() -> publisher(properties, validGenerator(new AtomicInteger())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("report-name");
  }

  @ParameterizedTest
  @ValueSource(strings = {"yyyy/MM/dd", "'unterminated", " "})
  void unsafeOrInvalidTimestampPatternsFailAtPreflight(String pattern) {
    AllureSingleFileProperties properties = properties();
    properties.setTimestampPattern(pattern);

    assertThatThrownBy(() -> publisher(properties, validGenerator(new AtomicInteger())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("timestamp-pattern");
  }

  @Test
  void timestampCollisionUsesDeterministicSuffixWithoutOverwrite() throws IOException {
    AllureSingleFileProperties firstProperties = properties();
    Path first =
        publisher(firstProperties, validGenerator(new AtomicInteger())).publishOnce().orElseThrow();
    String original = Files.readString(first);
    AllureSingleFileProperties secondProperties = properties();

    Path second =
        publisher(secondProperties, validGenerator(new AtomicInteger()))
            .publishOnce()
            .orElseThrow();

    assertThat(second.getParent().getFileName()).hasToString("202608100245-2");
    assertThat(Files.readString(first)).isEqualTo(original);
    assertThat(second).isNotEqualTo(first).isNotEmptyFile();
  }

  @Test
  void concurrentAndDuplicateRunnerCallbacksGenerateExactlyOnce() throws Exception {
    AtomicInteger generations = new AtomicInteger();
    AllureSingleFilePublisher publisher = publisher(properties(), validGenerator(generations));

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Callable<Path>> callbacks =
          Stream.generate(() -> (Callable<Path>) () -> publisher.publishOnce().orElseThrow())
              .limit(20)
              .toList();
      List<Path> results =
          executor.invokeAll(callbacks).stream().map(AllureSingleFilePublisherTest::get).toList();
      assertThat(results).allMatch(results.getFirst()::equals);
    }
    assertThat(generations).hasValue(1);
  }

  @ParameterizedTest(name = "{0} completion publishes once")
  @ValueSource(strings = {"TestNG", "Cucumber", "TestNG-orchestrated Cucumber"})
  void oneTerminalListenerEventPublishesOnceForEachApprovedRunnerBoundary(String runner) {
    AtomicInteger generations = new AtomicInteger();
    AllureSingleFilePublisher publisher = publisher(properties(), validGenerator(generations));
    AllureSingleFileRunCoordinator.register(publisher);
    try {
      AllureSingleFileExecutionListener listener = new AllureSingleFileExecutionListener();
      listener.onExecutionStart();
      listener.onExecutionFinish();
      listener.onExecutionFinish();
      assertThat(publisher.publishedArtifact()).isPresent();
      assertThat(generations).as(runner).hasValue(1);
    } finally {
      AllureSingleFileRunCoordinator.unregister(publisher);
    }
  }

  @Test
  void generatorFailureIsActionableAndRetainsResults() {
    AllureSingleFileProperties properties = properties();
    SingleFileReportGenerator failing =
        request -> {
          throw new AllureSingleFilePublicationException(
              "generator-exit", "Allure CLI returned exit code 2; verify compatibility");
        };

    assertThatThrownBy(() -> publisher(properties, failing).publishOnce())
        .isInstanceOf(AllureSingleFilePublicationException.class)
        .hasMessageContaining("generator-exit")
        .hasMessageContaining("verify compatibility");
    assertThat(properties.getResultsDirectory().resolve("source-result.json")).exists();
  }

  @Test
  void invalidGeneratorArtifactIsNotReportedAsSuccess() {
    AllureSingleFileProperties properties = properties();
    SingleFileReportGenerator invalid =
        request -> {
          try {
            Path artifact = request.stagingDirectory().resolve("index.html");
            Files.writeString(artifact, "not a report");
            return artifact;
          } catch (IOException failure) {
            throw new RuntimeException(failure);
          }
        };

    assertThatThrownBy(() -> publisher(properties, invalid).publishOnce())
        .isInstanceOf(AllureSingleFilePublicationException.class)
        .hasMessageContaining("artifact-validation");
  }

  private AllureSingleFileProperties properties() {
    AllureSingleFileProperties properties = new AllureSingleFileProperties();
    properties.setEnabled(true);
    properties.setOutputDirectory(temporary.resolve("reports"));
    properties.setResultsDirectory(temporary.resolve("allure-results"));
    try {
      Files.createDirectories(properties.getResultsDirectory());
      Files.writeString(
          properties.getResultsDirectory().resolve("source-result.json"),
          "{\"sanitized\":\"[REDACTED]\"}");
    } catch (IOException failure) {
      throw new RuntimeException(failure);
    }
    return properties;
  }

  private static AllureSingleFilePublisher publisher(
      AllureSingleFileProperties properties, SingleFileReportGenerator generator) {
    return new AllureSingleFilePublisher(properties, generator, CLOCK);
  }

  private static SingleFileReportGenerator validGenerator(AtomicInteger generations) {
    return request -> {
      generations.incrementAndGet();
      Path artifact = request.stagingDirectory().resolve("index.html");
      try {
        Files.writeString(
            artifact,
            "<!doctype html><html><body><main id=\"allure\"></main><script>window.allure={}</script></body></html>",
            StandardCharsets.UTF_8);
        return artifact;
      } catch (IOException failure) {
        throw new RuntimeException(failure);
      }
    };
  }

  private static Path get(java.util.concurrent.Future<Path> future) {
    try {
      return future.get();
    } catch (Exception failure) {
      throw new RuntimeException(failure);
    }
  }
}

package com.codinglair.taf.runtime.core.reporting.impl.allure;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.reporting.impl.allure.support.Rep005CucumberRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testng.TestNG;

class AllureSingleFileRunnerBoundaryIntegrationTest {
  @TempDir Path temporary;

  @Test
  void ordinaryTestNgExecutionPublishesExactlyOnce() throws Exception {
    assertOnePublication(OrdinaryTest.class);
  }

  @Test
  void cucumberThroughTestNgPublishesExactlyOnceWithoutDuplicateCallback() throws Exception {
    assertOnePublication(Rep005CucumberRunner.class);
  }

  private void assertOnePublication(Class<?> testClass) throws Exception {
    AtomicInteger generations = new AtomicInteger();
    AllureSingleFileProperties properties = new AllureSingleFileProperties();
    properties.setEnabled(true);
    properties.setResultsDirectory(temporary.resolve(testClass.getSimpleName() + "-results"));
    properties.setOutputDirectory(temporary.resolve(testClass.getSimpleName() + "-reports"));
    Files.createDirectories(properties.getResultsDirectory());
    Files.writeString(
        properties.getResultsDirectory().resolve("sanitized-result.json"), "[REDACTED]");
    SingleFileReportGenerator generator =
        request -> {
          generations.incrementAndGet();
          Path artifact = request.stagingDirectory().resolve("index.html");
          try {
            Files.writeString(
                artifact,
                "<!doctype html><html><main id=\"allure\"></main><script>allure={}</script></html>");
            return artifact;
          } catch (java.io.IOException failure) {
            throw new RuntimeException(failure);
          }
        };
    AllureSingleFilePublisher publisher =
        new AllureSingleFilePublisher(
            properties,
            generator,
            Clock.fixed(Instant.parse("2026-08-10T02:45:00Z"), ZoneOffset.UTC));
    AllureSingleFileRunCoordinator.register(publisher);
    try {
      TestNG testng = new TestNG(false);
      testng.setUseDefaultListeners(false);
      testng.setTestClasses(new Class<?>[] {testClass});
      testng.run();
      assertThat(testng.hasFailure()).isFalse();
      assertThat(generations).hasValue(1);
      assertThat(publisher.publishedArtifact()).isPresent();
    } finally {
      AllureSingleFileRunCoordinator.unregister(publisher);
    }
  }

  public static final class OrdinaryTest {
    @Test
    public void passes() {}
  }
}

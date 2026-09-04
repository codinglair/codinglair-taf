package com.codinglair.taf.runtime.core.reporting;

import static org.junit.jupiter.api.Assertions.*;

import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestReporter;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestStep;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;

/**
 * Unit tests for ArtifactCollector. Validates artifact collection, step management, and
 * thread-safety.
 */
class ArtifactCollectorTest {

  @Test
  @DisplayName("Constructor sets test and session IDs")
  void constructor_setsTestAndSessionIds() {
    TafTest test = TafTest.of("test1", "TestClass1");
    ArtifactCollector collector = new ArtifactCollector(test, "session1", "test1");

    assertEquals("session1", collector.getSessionId());
    assertEquals("test1", collector.getTestId());
    assertEquals("test1", collector.getTest().name());
    assertEquals("TestClass1", collector.getTest().className());
  }

  @Test
  @DisplayName("Add step adds step to collection")
  void addStep_addsStepToCollection() {
    TafTest test = TafTest.of("test1", "TestClass1");
    ArtifactCollector collector = new ArtifactCollector(test, "session1", "test1");

    TestStep step1 = TestStep.of("step1", "PASSED", "First step");
    TestStep step2 = TestStep.of("step2", "FAILED", "Second step");

    collector.addStep(step1).addStep(step2);

    assertEquals(2, collector.getSteps().size());
    assertEquals("step1", collector.getSteps().get(0).name());
    assertEquals("PASSED", collector.getSteps().get(0).status());
    assertEquals("step2", collector.getSteps().get(1).name());
    assertEquals("FAILED", collector.getSteps().get(1).status());
  }

  @Test
  @DisplayName("Add artifact adds artifact to collection")
  void addArtifact_addsArtifactToCollection() {
    TafTest test = TafTest.of("test1", "TestClass1");
    ArtifactCollector collector = new ArtifactCollector(test, "session1", "test1");

    TestArtifact artifact =
        TestArtifact.of("artifact1", "SCREENSHOT", "screenshot.png", "image/png");

    collector.addArtifact(artifact);

    assertEquals(1, collector.getArtifacts().size());
    assertEquals("artifact1", collector.getArtifacts().get(0).name());
    assertEquals("SCREENSHOT", collector.getArtifacts().get(0).type());
    assertEquals("image/png", collector.getArtifacts().get(0).contentType());
  }

  @Test
  void sanitizesArtifactMetadataAndContentBeforeAuthoritativeStorage() {
    ArtifactCollector collector =
        new ArtifactCollector(TafTest.of("test", "TestClass"), "session", "test");
    collector.addArtifact("password=hunter2", "log", "token=abc123", "text/plain", "secret=value");

    TestArtifact stored = collector.getArtifacts().getFirst();
    assertEquals("****", stored.name());
    assertEquals("****", stored.content());
    assertEquals("****", stored.stepName());
    assertEquals(TestArtifact.computeHash("****"), stored.hash());
  }

  @Test
  void finalizesEvidenceExactlyOnce() {
    ArtifactCollector collector =
        new ArtifactCollector(TafTest.of("test", "TestClass"), "session", "test");
    collector.addStep(TestStep.of("last successful", "PASSED"));
    collector.addArtifact("failure", "log", "safe", "text/plain", "failing validation");
    CountingReporter reporter = new CountingReporter();
    ReporterDispatcher dispatcher = new ReporterDispatcher(reporter, new RedactionPipeline());

    collector.finalizeEvidence(dispatcher);
    collector.finalizeEvidence(dispatcher);

    assertEquals(1, reporter.steps);
    assertEquals(1, reporter.artifacts);
  }

  private static final class CountingReporter implements TestReporter {
    private int steps;
    private int artifacts;

    @Override
    public void beginTest(TafTest test) {}

    @Override
    public void endTest(TafTest test) {}

    @Override
    public void reportStep(TestStep step) {
      steps++;
    }

    @Override
    public void reportArtifact(TestArtifact artifact) {
      artifacts++;
    }

    @Override
    public String getName() {
      return "counting";
    }
  }

  @Test
  @DisplayName("Multiple artifacts per step - multiple artifacts attached")
  void multipleArtifactsPerStep_multipleArtifactsAttached() {
    TafTest test = TafTest.of("test1", "TestClass1");
    ArtifactCollector collector = new ArtifactCollector(test, "session1", "test1");

    TestStep step = TestStep.of("step1", "PASSED", "Test step with multiple artifacts");
    collector.addStep(step);

    collector.addArtifact(TestArtifact.of("artifact1", "LOG", "log content", "text/plain"));
    collector.addArtifact(
        TestArtifact.of("artifact2", "SCREENSHOT", "screenshot.png", "image/png"));
    collector.addArtifact(TestArtifact.of("artifact3", "VIDEO", "video.mp4", "video/mp4"));

    assertEquals(3, collector.getArtifacts().size());
  }

  @Test
  @DisplayName("Sequence number increments with each call")
  void sequenceNumber_incrementsWithEachCall() {
    TafTest test = TafTest.of("test1", "TestClass1");
    ArtifactCollector collector = new ArtifactCollector(test, "session1", "test1");

    assertEquals(0, collector.getSequenceNumber());
    assertEquals(1, collector.nextSequenceNumber());
    assertEquals(2, collector.nextSequenceNumber());
    assertEquals(3, collector.nextSequenceNumber());
  }

  @Test
  @DisplayName("Rerun attempt increments with each call")
  void rerunAttempt_incrementsWithEachCall() {
    TafTest test = TafTest.of("test1", "TestClass1");
    ArtifactCollector collector = new ArtifactCollector(test, "session1", "test1");

    assertEquals(0, collector.getRerunAttempt());
    assertEquals(1, collector.nextRerunAttempt());
    assertEquals(2, collector.nextRerunAttempt());
  }

  @Test
  @DisplayName("To structured result creates correct structure")
  void toStructuredResult_createsCorrectStructure() {
    TafTest test = TafTest.of("test1", "TestClass1");
    ArtifactCollector collector = new ArtifactCollector(test, "session1", "test1");

    collector.addStep(TestStep.of("step1", "PASSED", "First step"));
    collector.addArtifact(TestArtifact.of("artifact1", "LOG", "log content", "text/plain"));

    Map<String, Object> result = collector.toStructuredResult();

    assertEquals("session1", result.get("sessionId"));
    assertEquals("test1", result.get("testId"));
    assertEquals("test1", result.get("testName"));
    assertEquals("TestClass1", result.get("className"));

    Map<String, Object> stepsResult = (Map<String, Object>) result.get("steps");
    assertNotNull(stepsResult);

    Map<String, Object> artifactsResult = (Map<String, Object>) result.get("artifacts");
    assertNotNull(artifactsResult);
  }

  @Test
  @DisplayName("Get steps returns immutable copy")
  void getSteps_returnsImmutableCopy() {
    TafTest test = TafTest.of("test1", "TestClass1");
    ArtifactCollector collector = new ArtifactCollector(test, "session1", "test1");

    collector.addStep(TestStep.of("step1", "PASSED", "First step"));

    List<TestStep> steps = collector.getSteps();
    assertNotNull(steps);
    assertFalse(steps.isEmpty());

    // Verify it's a copy by modifying the original
    assertThrows(
        UnsupportedOperationException.class,
        () -> steps.add(TestStep.of("step2", "FAILED", "Second step")));
    assertEquals(1, collector.getSteps().size());
  }

  @Test
  @DisplayName("Get artifacts returns immutable copy")
  void getArtifacts_returnsImmutableCopy() {
    TafTest test = TafTest.of("test1", "TestClass1");
    ArtifactCollector collector = new ArtifactCollector(test, "session1", "test1");

    collector.addArtifact(TestArtifact.of("artifact1", "LOG", "log content", "text/plain"));

    List<TestArtifact> artifacts = collector.getArtifacts();
    assertNotNull(artifacts);
    assertFalse(artifacts.isEmpty());

    // Verify it's a copy by modifying the original
    assertThrows(
        UnsupportedOperationException.class,
        () ->
            artifacts.add(
                TestArtifact.of("artifact2", "SCREENSHOT", "screenshot.png", "image/png")));
    assertEquals(1, collector.getArtifacts().size());
  }

  // === Concurrency Tests ===

  @Test
  @DisplayName("ArtifactCollector handles concurrent step additions")
  void testConcurrentStepAddition() throws Exception {
    TafTest test = TafTest.of("test1", "TestClass1");
    ArtifactCollector collector = new ArtifactCollector(test, "session1", "test1");

    int numThreads = 10;
    int stepsPerThread = 100;

    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < numThreads; i++) {
      final int threadIndex = i;
      Thread t =
          new Thread(
              () -> {
                for (int j = 0; j < stepsPerThread; j++) {
                  collector.addStep(
                      TestStep.of("step-" + threadIndex + "-" + j, "PASSED", "Concurrent step"));
                }
              });
      t.start();
      threads.add(t);
    }

    for (Thread t : threads) {
      t.join(30000);
    }

    assertEquals(numThreads * stepsPerThread, collector.getSteps().size());
  }

  @Test
  @DisplayName("ArtifactCollector handles concurrent artifact additions")
  void testConcurrentArtifactAddition() throws Exception {
    TafTest test = TafTest.of("test1", "TestClass1");
    ArtifactCollector collector = new ArtifactCollector(test, "session1", "test1");

    int numThreads = 10;
    int artifactsPerThread = 100;

    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < numThreads; i++) {
      final int threadIndex = i;
      Thread t =
          new Thread(
              () -> {
                for (int j = 0; j < artifactsPerThread; j++) {
                  collector.addArtifact(
                      TestArtifact.of(
                          "artifact-" + threadIndex + "-" + j, "LOG", "log content", "text/plain"));
                }
              });
      t.start();
      threads.add(t);
    }

    for (Thread t : threads) {
      t.join(30000);
    }

    assertEquals(numThreads * artifactsPerThread, collector.getArtifacts().size());
  }

  @Test
  @DisplayName("ArtifactCollector handles mixed concurrent step and artifact operations")
  void testMixedConcurrentOperations() throws Exception {
    TafTest test = TafTest.of("test1", "TestClass1");
    ArtifactCollector collector = new ArtifactCollector(test, "session1", "test1");

    int numThreads = 10;
    int operationsPerThread = 100;

    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < numThreads; i++) {
      final int threadIndex = i;
      Thread t =
          new Thread(
              () -> {
                for (int j = 0; j < operationsPerThread; j++) {
                  if (j % 2 == 0) {
                    collector.addStep(
                        TestStep.of("step-" + threadIndex + "-" + j, "PASSED", "Concurrent step"));
                  } else {
                    collector.addArtifact(
                        TestArtifact.of(
                            "artifact-" + threadIndex + "-" + j,
                            "LOG",
                            "log content",
                            "text/plain"));
                  }
                }
              });
      t.start();
      threads.add(t);
    }

    for (Thread t : threads) {
      t.join(30000);
    }

    assertEquals(numThreads * operationsPerThread / 2, collector.getSteps().size());
    assertEquals(numThreads * operationsPerThread / 2, collector.getArtifacts().size());
  }

  @Test
  @DisplayName("ArtifactCollector maintains sequence number consistency under concurrency")
  void testConcurrentSequenceNumberConsistency() throws Exception {
    TafTest test = TafTest.of("test1", "TestClass1");
    ArtifactCollector collector = new ArtifactCollector(test, "session1", "test1");

    int numThreads = 5;
    int operationsPerThread = 50;

    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < numThreads; i++) {
      final int threadIndex = i;
      Thread t =
          new Thread(
              () -> {
                for (int j = 0; j < operationsPerThread; j++) {
                  collector.nextSequenceNumber();
                }
              });
      t.start();
      threads.add(t);
    }

    for (Thread t : threads) {
      t.join(30000);
    }

    long finalSequence = collector.getSequenceNumber();
    assertEquals((long) numThreads * operationsPerThread, finalSequence);
  }

  @Test
  @DisplayName("ArtifactCollector handles concurrent rerun attempts")
  void testConcurrentRerunAttempts() throws Exception {
    TafTest test = TafTest.of("test1", "TestClass1");
    ArtifactCollector collector = new ArtifactCollector(test, "session1", "test1");

    int numThreads = 5;
    int attemptsPerThread = 20;

    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < numThreads; i++) {
      final int threadIndex = i;
      Thread t =
          new Thread(
              () -> {
                for (int j = 0; j < attemptsPerThread; j++) {
                  collector.nextRerunAttempt();
                }
              });
      t.start();
      threads.add(t);
    }

    for (Thread t : threads) {
      t.join(30000);
    }

    long finalRerun = collector.getRerunAttempt();
    assertEquals((long) numThreads * attemptsPerThread, finalRerun);
  }

  @Test
  @DisplayName("ArtifactCollector toStructuredResult is thread-safe")
  void testStructuredResultThreadSafety() throws Exception {
    TafTest test = TafTest.of("test1", "TestClass1");
    ArtifactCollector collector = new ArtifactCollector(test, "session1", "test1");

    // Add some steps and artifacts
    collector.addStep(TestStep.of("step1", "PASSED", "Step 1"));
    collector.addArtifact(TestArtifact.of("artifact1", "LOG", "log", "text/plain"));

    int numThreads = 10;
    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < numThreads; i++) {
      final int threadIndex = i;
      Thread t =
          new Thread(
              () -> {
                collector.toStructuredResult(); // void lambda - just call toStructuredResult
              });
      t.start();
      threads.add(t);
    }

    for (Thread t : threads) {
      t.join(30000);
    }

    // All results should be consistent
    Map<String, Object> result1 = collector.toStructuredResult();
    Map<String, Object> result2 = collector.toStructuredResult();

    assertEquals(result1.get("sessionId"), result2.get("sessionId"));
    assertEquals(result1.get("testId"), result2.get("testId"));
  }
}

package com.codinglair.taf.runtime.core.reporting;

import static org.junit.jupiter.api.Assertions.*;

import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestReporter;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestStep;
import org.junit.jupiter.api.*;

/**
 * Contract tests for the TestReporter SPI. These tests validate that any implementation of
 * TestReporter maintains the expected behavior regardless of vendor.
 */
class ReporterContractTest {

  // === Reporter Failure Isolation Tests ===

  @Test
  @DisplayName("Reporter failure does not corrupt ArtifactCollector state")
  void testReporterFailureDoesNotCorruptCollectorState() {
    TafTest test = TafTest.of("test1", "TestClass1");
    TestArtifact artifact = TestArtifact.of("artifact1", "LOG", "log content", "text/plain");
    TestStep step = TestStep.of("step1", "PASSED", "First step");

    ArtifactCollector collector = new ArtifactCollector(test, "session1", "test1");
    collector.addStep(step);
    collector.addArtifact(artifact);

    // Capture collector state before reporter failure
    long stepsCountBefore = collector.getSteps().size();
    long artifactsCountBefore = collector.getArtifacts().size();

    // Create a throwing reporter that throws on first call
    TestReporter throwingReporter =
        new TestReporter() {
          private boolean threwException = false;

          @Override
          public void beginTest(TafTest test) {
            if (!threwException) {
              threwException = true;
              throw new RuntimeException("Simulated reporter failure in beginTest");
            }
          }

          @Override
          public void endTest(TafTest test) {
            throw new RuntimeException("Simulated reporter failure in endTest");
          }

          @Override
          public void reportStep(TestStep step) {
            throw new RuntimeException("Simulated reporter failure in reportStep");
          }

          @Override
          public void reportArtifact(TestArtifact artifact) {
            throw new RuntimeException("Simulated reporter failure in reportArtifact");
          }

          @Override
          public String getName() {
            return "Throwing Reporter";
          }
        };

    // Calling reporter methods should throw exceptions
    assertThrows(RuntimeException.class, () -> throwingReporter.beginTest(test));
    assertThrows(RuntimeException.class, () -> throwingReporter.reportStep(step));
    assertThrows(RuntimeException.class, () -> throwingReporter.reportArtifact(artifact));
    assertThrows(RuntimeException.class, () -> throwingReporter.endTest(test));

    // Collector state should remain intact
    long stepsCountAfter = collector.getSteps().size();
    long artifactsCountAfter = collector.getArtifacts().size();

    assertEquals(stepsCountBefore, stepsCountAfter, "Steps count should not change");
    assertEquals(artifactsCountBefore, artifactsCountAfter, "Artifacts count should not change");
  }

  @Test
  @DisplayName("Reporter handles multiple failures correctly")
  void testReporterHandlesMultipleFailures() {
    TestReporter throwingReporter =
        new TestReporter() {
          private int callCount = 0;

          @Override
          public void beginTest(TafTest test) {
            callCount++;
            if (callCount == 1) {
              throw new RuntimeException("Failure 1");
            }
          }

          @Override
          public void endTest(TafTest test) {
            // no-op
          }

          @Override
          public void reportStep(TestStep step) {
            // no-op
          }

          @Override
          public void reportArtifact(TestArtifact artifact) {
            // no-op
          }

          @Override
          public String getName() {
            return "Multi Failure Reporter";
          }
        };

    TafTest test = TafTest.of("test1", "TestClass1");
    TestStep step = TestStep.of("step1", "PASSED", "First step");

    // First call should throw
    assertThrows(RuntimeException.class, () -> throwingReporter.beginTest(test));

    // Subsequent calls should not throw
    assertDoesNotThrow(() -> throwingReporter.reportStep(step));
    assertDoesNotThrow(() -> throwingReporter.endTest(test));
  }

  // === Existing Contract Tests ===

  @Test
  @DisplayName("TestReporter interface requires all required methods")
  void testReporterInterfaceRequiresAllRequiredMethods() {
    TestReporter reporter =
        new TestReporter() {
          @Override
          public void beginTest(TafTest test) {
            // no-op
          }

          @Override
          public void endTest(TafTest test) {
            // no-op
          }

          @Override
          public void reportStep(TestStep step) {
            // no-op
          }

          @Override
          public void reportArtifact(TestArtifact artifact) {
            // no-op
          }

          @Override
          public String getName() {
            return "Contract Test Reporter";
          }
        };

    assertNotNull(reporter.getName());
    assertEquals("Contract Test Reporter", reporter.getName());

    // Verify all methods can be called without exception
    TafTest test = TafTest.of("test1", "TestClass1");
    TestStep step = TestStep.of("step1", "PASSED", "First step");
    TestArtifact artifact = TestArtifact.of("artifact1", "LOG", "log content", "text/plain");

    assertDoesNotThrow(() -> reporter.beginTest(test));
    assertDoesNotThrow(() -> reporter.endTest(test));
    assertDoesNotThrow(() -> reporter.reportStep(step));
    assertDoesNotThrow(() -> reporter.reportArtifact(artifact));
  }

  @Test
  @DisplayName("TestReporter handles null inputs")
  void testReporterHandlesNullInputs() {
    TestReporter reporter =
        new TestReporter() {
          @Override
          public void beginTest(TafTest test) {
            // no-op
          }

          @Override
          public void endTest(TafTest test) {
            // no-op
          }

          @Override
          public void reportStep(TestStep step) {
            // no-op
          }

          @Override
          public void reportArtifact(TestArtifact artifact) {
            // no-op
          }

          @Override
          public String getName() {
            return "Null Safe Reporter";
          }
        };

    // Should not throw NullPointerException
    assertDoesNotThrow(() -> reporter.beginTest(null));
    assertDoesNotThrow(() -> reporter.endTest(null));
    assertDoesNotThrow(() -> reporter.reportStep(null));
    assertDoesNotThrow(() -> reporter.reportArtifact(null));
    assertDoesNotThrow(() -> reporter.getName());
  }

  @Test
  @DisplayName("TestReporter supports multiple steps and artifacts")
  void testReporterSupportsMultipleStepsAndArtifacts() {
    TestReporter reporter =
        new TestReporter() {
          @Override
          public void beginTest(TafTest test) {
            // no-op
          }

          @Override
          public void endTest(TafTest test) {
            // no-op
          }

          @Override
          public void reportStep(TestStep step) {
            // no-op
          }

          @Override
          public void reportArtifact(TestArtifact artifact) {
            // no-op
          }

          @Override
          public String getName() {
            return "Multi Step Reporter";
          }
        };

    TafTest test = TafTest.of("test1", "TestClass1");
    TestStep step1 = TestStep.of("step1", "PASSED", "First step");
    TestStep step2 = TestStep.of("step2", "FAILED", "Second step");
    TestStep step3 = TestStep.of("step3", "PASSED", "Third step");

    assertDoesNotThrow(() -> reporter.beginTest(test));
    assertDoesNotThrow(() -> reporter.reportStep(step1));
    assertDoesNotThrow(() -> reporter.reportStep(step2));
    assertDoesNotThrow(() -> reporter.reportStep(step3));

    TestArtifact artifact1 = TestArtifact.of("artifact1", "LOG", "log1", "text/plain");
    TestArtifact artifact2 =
        TestArtifact.of("artifact2", "SCREENSHOT", "screenshot.png", "image/png");

    assertDoesNotThrow(() -> reporter.reportArtifact(artifact1));
    assertDoesNotThrow(() -> reporter.reportArtifact(artifact2));
  }

  @Test
  @DisplayName("TestReporter handles exceptions in beginTest")
  void testReporterHandlesExceptionsInBeginTest() {
    TestReporter reporter =
        new TestReporter() {
          @Override
          public void beginTest(TafTest test) {
            throw new IllegalStateException("Simulated failure in beginTest");
          }

          @Override
          public void endTest(TafTest test) {
            // no-op
          }

          @Override
          public void reportStep(TestStep step) {
            // no-op
          }

          @Override
          public void reportArtifact(TestArtifact artifact) {
            // no-op
          }

          @Override
          public String getName() {
            return "Exceptional Reporter";
          }
        };

    TafTest test = TafTest.of("test1", "TestClass1");

    // Exception is expected and captured
    Exception caught = assertThrows(IllegalStateException.class, () -> reporter.beginTest(test));
    assertEquals("Simulated failure in beginTest", caught.getMessage());
  }

  @Test
  @DisplayName("TestReporter handles exceptions in endTest")
  void testReporterHandlesExceptionsInEndTest() {
    TestReporter reporter =
        new TestReporter() {
          @Override
          public void beginTest(TafTest test) {
            // no-op
          }

          @Override
          public void endTest(TafTest test) {
            throw new IllegalStateException("Simulated failure in endTest");
          }

          @Override
          public void reportStep(TestStep step) {
            // no-op
          }

          @Override
          public void reportArtifact(TestArtifact artifact) {
            // no-op
          }

          @Override
          public String getName() {
            return "Exceptional Reporter 2";
          }
        };

    TafTest test = TafTest.of("test1", "TestClass1");

    // Begin test successfully
    assertDoesNotThrow(() -> reporter.beginTest(test));

    // Exception is expected in endTest
    Exception caught = assertThrows(IllegalStateException.class, () -> reporter.endTest(test));
    assertEquals("Simulated failure in endTest", caught.getMessage());
  }

  @Test
  @DisplayName("TestReporter handles exceptions in reportStep")
  void testReporterHandlesExceptionsInReportStep() {
    TestReporter reporter =
        new TestReporter() {
          @Override
          public void beginTest(TafTest test) {
            // no-op
          }

          @Override
          public void endTest(TafTest test) {
            // no-op
          }

          @Override
          public void reportStep(TestStep step) {
            throw new IllegalStateException("Simulated failure in reportStep");
          }

          @Override
          public void reportArtifact(TestArtifact artifact) {
            // no-op
          }

          @Override
          public String getName() {
            return "Exceptional Reporter 3";
          }
        };

    TafTest test = TafTest.of("test1", "TestClass1");

    assertDoesNotThrow(() -> reporter.beginTest(test));

    // Exception is expected in reportStep
    Exception caught =
        assertThrows(
            IllegalStateException.class, () -> reporter.reportStep(TestStep.of("step1", "PASSED")));
    assertEquals("Simulated failure in reportStep", caught.getMessage());
  }

  @Test
  @DisplayName("TestReporter handles exceptions in reportArtifact")
  void testReporterHandlesExceptionsInReportArtifact() {
    TestReporter reporter =
        new TestReporter() {
          @Override
          public void beginTest(TafTest test) {
            // no-op
          }

          @Override
          public void endTest(TafTest test) {
            // no-op
          }

          @Override
          public void reportStep(TestStep step) {
            // no-op
          }

          @Override
          public void reportArtifact(TestArtifact artifact) {
            throw new IllegalStateException("Simulated failure in reportArtifact");
          }

          @Override
          public String getName() {
            return "Exceptional Reporter 4";
          }
        };

    TafTest test = TafTest.of("test1", "TestClass1");

    assertDoesNotThrow(() -> reporter.beginTest(test));

    // Exception is expected in reportArtifact
    Exception caught =
        assertThrows(
            IllegalStateException.class,
            () ->
                reporter.reportArtifact(
                    TestArtifact.of("artifact1", "LOG", "log content", "text/plain")));
    assertEquals("Simulated failure in reportArtifact", caught.getMessage());
  }
}

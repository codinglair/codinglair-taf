package com.codinglair.taf.runtime.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.cucumber.glue.AccountSteps;
import com.codinglair.taf.runtime.cucumber.suites.OrdinaryTechnicalTest;
import com.codinglair.taf.runtime.cucumber.support.CapturingBusinessResultListener;
import com.codinglair.taf.runtime.testng.TestNgExecutionResult;
import com.codinglair.taf.runtime.testng.TestNgLifecycleListener;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testng.TestNG;

class CucumberTestNgXmlIntegrationTest {
  @BeforeEach
  void resetEvidence() {
    AccountSteps.CLEANUPS.set(0);
    OrdinaryTechnicalTest.INVOCATIONS.set(0);
    CapturingBusinessResultListener.reset();
  }

  @Test
  void testNgXmlExecutesCucumberRunnerWithExactlyOneScenarioLifecycleAndBusinessResult() {
    List<TestNgExecutionResult> technicalResults = new CopyOnWriteArrayList<>();

    TestNG testng = runSuite("suites/cucumber-suite-a.xml", technicalResults);

    assertThat(testng.hasFailure()).isFalse();
    assertThat(AccountSteps.CLEANUPS).hasValue(1);
    assertThat(CapturingBusinessResultListener.results())
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.scenarioName()).isEqualTo("Suite A business behavior");
              assertThat(result.traceabilityTags()).contains("@requirement-SUITE-101");
            });
    assertThat(technicalResults)
        .as("Cucumber runScenario must not enter technical reporting")
        .isEmpty();
  }

  @Test
  void differentXmlSuitesSelectDifferentCucumberRunnersWithoutPomChanges() {
    TestNG first = runSuite("suites/cucumber-suite-a.xml", new CopyOnWriteArrayList<>());
    assertThat(first.hasFailure()).isFalse();
    assertThat(CapturingBusinessResultListener.results())
        .extracting(CucumberBusinessResult::scenarioName)
        .containsExactly("Suite A business behavior");

    CapturingBusinessResultListener.reset();
    AccountSteps.CLEANUPS.set(0);
    TestNG second = runSuite("suites/cucumber-suite-b.xml", new CopyOnWriteArrayList<>());

    assertThat(second.hasFailure()).isFalse();
    assertThat(AccountSteps.CLEANUPS).hasValue(1);
    assertThat(CapturingBusinessResultListener.results())
        .extracting(CucumberBusinessResult::scenarioName)
        .containsExactly("Suite B business behavior");
  }

  @Test
  void combinedXmlKeepsOrdinaryTestNgTestOutOfDedicatedBusinessResults() {
    List<TestNgExecutionResult> technicalResults = new CopyOnWriteArrayList<>();

    TestNG testng = runSuite("suites/combined-suite.xml", technicalResults);

    assertThat(testng.hasFailure()).isFalse();
    assertThat(OrdinaryTechnicalTest.INVOCATIONS).hasValue(1);
    assertThat(CapturingBusinessResultListener.results())
        .extracting(CucumberBusinessResult::scenarioName)
        .containsExactly("Suite A business behavior");
    assertThat(technicalResults)
        .singleElement()
        .extracting(TestNgExecutionResult::testName)
        .isEqualTo("technicalBoundaryCheck");
  }

  private static TestNG runSuite(String resource, List<TestNgExecutionResult> technicalResults) {
    TestNG testng = new TestNG(false);
    testng.setUseDefaultListeners(false);
    testng.setServiceLoaderClassLoader(new URLClassLoader(new URL[0], null));
    testng.addListener(new TestNgLifecycleListener(List.of(), List.of(technicalResults::add)));
    testng.setTestSuites(List.of(resourcePath(resource).toString()));
    testng.run();
    return testng;
  }

  private static Path resourcePath(String resource) {
    try {
      return Path.of(
          CucumberTestNgXmlIntegrationTest.class.getClassLoader().getResource(resource).toURI());
    } catch (Exception failure) {
      throw new IllegalStateException("Missing TestNG XML suite " + resource, failure);
    }
  }
}

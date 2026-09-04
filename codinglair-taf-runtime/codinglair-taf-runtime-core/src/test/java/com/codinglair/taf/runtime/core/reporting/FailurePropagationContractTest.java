package com.codinglair.taf.runtime.core.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.failure.FailureAnalysis;
import com.codinglair.taf.runtime.core.failure.FailureContext;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestReporter;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestStep;
import java.util.List;
import org.junit.jupiter.api.Test;
import java.io.StringReader;
import org.xml.sax.InputSource;

class FailurePropagationContractTest {
  @Test
  void runtimeJsonJUnitXmlAndReporterUseTheSameAuthoritativeAnalysis() throws Exception {
    FailureAnalysis analysis = synthetic();
    assertThat(analysis.signature().value()).isEqualTo("failure-signature:v1:9177a40eb1d9f7a5a2731cae8cb9e08a9d276b5b73e3af19114a1097fb5c3439");
    var writer = new StructuredResultWriter();
    String json = writer.writeJson("session", "test", "synthetic", List.of(), List.of(), analysis);
    String xml = writer.writeJUnitXml("session", "test", "synthetic", "Example", List.of(), analysis);
    assertThat(json).doesNotMatch("(?s).*,\\s*[}\\]].*");
    var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    assertThat(document.getDocumentElement().getTagName()).isEqualTo("testsuite");
    assertThat(document.getElementsByTagName("properties").getLength()).isOne();
    assertThat(document.getElementsByTagName("step").getLength()).isZero();
    for (String value : List.of(analysis.classification().type().name(), analysis.classification().source().name(),
        analysis.stability().name(), analysis.historyStatus().name(), analysis.signature().value(), analysis.signature().algorithm())) {
      assertThat(json).contains(value);
      assertThat(xml).contains(value);
    }
    var reporter = new CapturingReporter();
    new ReporterDispatcher(reporter, new RedactionPipeline()).reportFailure(analysis);
    assertThat(reporter.analysis).isSameAs(analysis);
  }

  @Test
  void reporterFailureCannotMutateAuthoritativeAnalysis() {
    FailureAnalysis analysis = synthetic();
    TestReporter failing = new CapturingReporter() { @Override public void reportFailure(FailureAnalysis ignored) { throw new IllegalStateException("adapter failed"); } };
    var dispatcher = new ReporterDispatcher(failing, new RedactionPipeline());
    dispatcher.reportFailure(analysis);
    assertThat(dispatcher.hasFailed()).isTrue();
    assertThat(dispatcher.getFailureCount()).isOne();
    assertThat(analysis.classification().type().name()).isEqualTo("INCONCLUSIVE");
    assertThat(analysis.signature()).isNotNull();
  }

  static FailureAnalysis synthetic() {
    RuntimeException failure = new RuntimeException("synthetic failure token=contract-canary");
    failure.setStackTrace(new StackTraceElement[] {new StackTraceElement("sample.Controller", "execute", "Controller.java", 42)});
    return FailureAnalysis.classify("web", "action", FailureContext.Boundary.UNKNOWN, failure);
  }

  private static class CapturingReporter implements TestReporter {
    private FailureAnalysis analysis;
    public void reportFailure(FailureAnalysis value) { analysis = value; }
    public void beginTest(TafTest test) {}
    public void endTest(TafTest test) {}
    public void reportStep(TestStep step) {}
    public void reportArtifact(TestArtifact artifact) {}
    public String getName() { return "capture"; }
  }
}

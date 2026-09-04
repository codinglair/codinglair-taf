package com.codinglair.taf.runtime.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.failure.FailureAnalysis;
import com.codinglair.taf.runtime.core.failure.FailureContext;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class CucumberFailurePropagationTest {
  @Test
  void businessResultPreservesTheExactAuthoritativeSyntheticAnalysis() {
    RuntimeException failure = new RuntimeException("synthetic failure token=contract-canary");
    failure.setStackTrace(new StackTraceElement[] {new StackTraceElement("sample.Controller", "execute", "Controller.java", 42)});
    FailureAnalysis analysis = FailureAnalysis.classify("web", "action", FailureContext.Boundary.UNKNOWN, failure);
    var result = new CucumberBusinessResult("scenario", "synthetic", URI.create("classpath:synthetic.feature"),
        1, "FAILED", List.of(), List.of(), List.of(), analysis);
    assertThat(result.failureAnalysis()).isSameAs(analysis);
    assertThat(result.failureAnalysis().classification().type().name()).isEqualTo("INCONCLUSIVE");
    assertThat(result.failureAnalysis().signature().value())
        .isEqualTo("failure-signature:v1:9177a40eb1d9f7a5a2731cae8cb9e08a9d276b5b73e3af19114a1097fb5c3439");
    assertThat(result.toString()).doesNotContain("contract-canary");
  }
}

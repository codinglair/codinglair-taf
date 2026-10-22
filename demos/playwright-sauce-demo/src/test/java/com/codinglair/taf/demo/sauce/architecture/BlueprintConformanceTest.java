package com.codinglair.taf.demo.sauce.architecture;

import com.codinglair.taf.conformance.ConformanceReport;
import com.codinglair.taf.conformance.ConsumerProjectValidator;
import java.nio.file.Path;

class BlueprintConformanceTest {
  @org.junit.jupiter.api.Test
  void generatedProjectConforms() {
    Path projectRoot = Path.of(System.getProperty("taf.consumer.projectRoot", "."));
    ConformanceReport report = new ConsumerProjectValidator().validate(projectRoot);
    if (!report.conforms()) throw new AssertionError(report.violations());
  }
}

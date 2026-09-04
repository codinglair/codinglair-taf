package __BASE_PACKAGE__.architecture;

import com.codinglair.taf.conformance.ConsumerProjectValidator;
import java.nio.file.Path;

class BlueprintConformanceTest {
  @org.junit.jupiter.api.Test
  void generatedProjectConforms() {
    com.codinglair.taf.conformance.ConformanceReport report =
        new ConsumerProjectValidator().validate(Path.of("."));
    if (!report.conforms()) throw new AssertionError(report.violations());
  }
}

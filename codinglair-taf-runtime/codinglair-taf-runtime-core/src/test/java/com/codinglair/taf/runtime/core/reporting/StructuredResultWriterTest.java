package com.codinglair.taf.runtime.core.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestStep;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Structured result writer")
class StructuredResultWriterTest {
  private final StructuredResultWriter writer = new StructuredResultWriter();

  @Nested
  @DisplayName("JSON output")
  class JsonOutput {
    @Test
    @DisplayName("preserves exact fields, escaping, null analysis, and array formatting")
    void preservesExactOutput() {
      String result =
          writer.writeJson(
              "session",
              "test",
              "name",
              List.of(TestStep.of("step\nname", "PASSED", "description")),
              List.of(TestArtifact.of("artifact", "LOG", "a\"b", "text/plain")));

      assertThat(result)
          .isEqualTo(
              """
              {
                "sessionId": "session",
                "testId": "test",
                "testName": "name",
                "steps": [
                  {
                    "name": "step\\nname",
                    "status": "PASSED",
                    "description": "description"
                  }
                ],
                "artifacts": [
                  {
                    "name": "artifact",
                    "type": "LOG",
                    "contentType": "text/plain",
                    "content": "a\\\"b"
                  }
                ],
                "failureAnalysis": null
              }""");
    }
  }

  @Nested
  @DisplayName("JUnit XML output")
  class JunitXmlOutput {
    @Test
    @DisplayName("preserves exact property order, escaping, and step output")
    void preservesExactOutput() {
      String result =
          writer.writeJUnitXml(
              "session&one",
              "test",
              "name<one>",
              "Example\"Class",
              List.of(TestStep.of("step&one", "PASSED")));

      assertThat(result)
          .isEqualTo(
              """
              <?xml version="1.0" encoding="UTF-8"?>
              <testsuite tests="1" name="name&lt;one&gt;" hostname="test-machine">
                <properties>
                    <property name="taf.session.id" value="session&amp;one"/>
                    <property name="taf.test.id" value="test"/>
                </properties>
                <testcase classname="Example&quot;Class" name="name&lt;one&gt;" time="0.001">
                  <system-out>step:step&amp;one=PASSED
              </system-out>
                </testcase>
              </testsuite>""");
    }
  }
}

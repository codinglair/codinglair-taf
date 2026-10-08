package com.codinglair.taf.runtime.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.reporting.ArtifactCollector;
import com.codinglair.taf.runtime.core.reporting.RedactionPipeline;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Resource and signed URL redaction before artifact persistence")
class ResourceRedactionTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://user:FAKE_REDACTION_CANARY@host/path",
        "https://host/path?signature=FAKE_REDACTION_CANARY",
        "{\"accessKey\":\"FAKE_REDACTION_CANARY\"}",
        "{\"password\":\"FAKE_REDACTION_CANARY\"}"
      })
  @DisplayName(
      "scrubs URI userinfo, query signatures and JSON credential fields in retained output")
  void scrub(String content) {
    assertThat(new RedactionPipeline().redact(content)).doesNotContain("FAKE_REDACTION_CANARY");
    var collector = new ArtifactCollector(TafTest.of("test", "class"), "session", "test");
    collector.addArtifact("artifact", "text", content, "text/plain", "step");
    assertThat(collector.toStructuredResult().toString()).doesNotContain("FAKE_REDACTION_CANARY");
  }
}

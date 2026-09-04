package com.codinglair.taf.runtime.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VersionedDefinitionSecurityTest {
  @Test
  void malformedSecretReferenceDoesNotLeakItsValueOrRewriteTheSource() throws Exception {
    String canary = "plaintext-canary-" + java.util.UUID.randomUUID();
    Path file =
        Path.of("target", "security", java.util.UUID.randomUUID().toString(), "definitions.json");
    Files.createDirectories(file.getParent());
    String content =
        """
        {
          "schemaVersion": 1,
          "authority": "git",
          "definitions": [{
            "caseId": "TC-1", "version": 1, "state": "DRAFT",
            "input": {}, "expectedOutput": {},
            "secretReferences": {"credential": "%s"},
            "correlations": {}, "payloadReferences": []
          }]
        }
        """
            .formatted(canary);
    Files.writeString(file, content);

    assertThatThrownBy(
            () ->
                new FileTestDefinitionRepository(
                    new FileTestDefinitionConfiguration(
                        file, DefinitionFileFormat.JSON, new RepositoryAuthority("git"))))
        .hasMessageNotContaining(canary);
    assertThat(Files.readString(file)).isEqualTo(content);
  }

  @Test
  void payloadReferenceValidatesChecksumSizeMediaTypeAndVersion() {
    assertThatThrownBy(
            () ->
                new PayloadReference(
                    "payload",
                    java.net.URI.create("../escape.pdf"),
                    "sha256:bad",
                    "not-a-type",
                    -1,
                    ""))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

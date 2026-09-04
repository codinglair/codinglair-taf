package com.codinglair.taf.runtime.secret;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.reporting.ArtifactCollector;
import com.codinglair.taf.runtime.core.reporting.StructuredResultWriter;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class SecretProducedOutputLeakTest {
  @Test
  void generatedCanariesAreAbsentFromLogsArtifactsJsonExceptionsAuditAndDtoBoundaries() {
    String plaintext = "plain-canary-" + UUID.randomUUID();
    String masterKey = "key-canary-" + UUID.randomUUID();
    Map<String, String> environment = Map.of("MASTER_KEY", masterKey);
    JasyptSecretProvider provider = new JasyptSecretProvider(environment::get, "MASTER_KEY");
    String ciphertext = provider.encryptForLocalDevelopment(plaintext.toCharArray());
    String rawReference = "secret://jasypt/" + ciphertext;
    List<SecretAuditEvent> audit = new ArrayList<>();
    DefaultSecretManager manager = new DefaultSecretManager(List.of(provider), audit::add);
    CapturingHandler logs = new CapturingHandler();
    Logger logger = Logger.getLogger(DefaultSecretManager.class.getName());
    logger.addHandler(logs);

    RuntimeException failure;
    try {
      try (ResolvedSecret resolved =
          manager.resolve(
              rawReference, new SecretRequestContext("leak-test", "session", "local", true))) {
        ArtifactCollector artifacts =
            new ArtifactCollector(TafTest.of("leak", "SecretLeakTest"), "session", "test");
        artifacts.addArtifact(
            TestArtifact.of("failure", "text", "password=" + resolved.useAsString()));
        String structured =
            new StructuredResultWriter()
                .writeJson(
                    "session", "test", "leak", artifacts.getSteps(), artifacts.getArtifacts());
        assertSafe(structured, plaintext, masterKey, ciphertext);
      }
      failure =
          org.junit.jupiter.api.Assertions.assertThrows(
              RuntimeException.class,
              () ->
                  new JasyptSecretProvider(environment::get, "MASTER_KEY")
                      .resolve(SecretReference.parse("secret://jasypt/not-valid-ciphertext")));
    } finally {
      logger.removeHandler(logs);
    }

    StringWriter stackTrace = new StringWriter();
    failure.printStackTrace(new PrintWriter(stackTrace));
    assertSafe(logs.content(), plaintext, masterKey, ciphertext);
    assertSafe(audit.toString(), plaintext, masterKey, ciphertext);
    assertSafe(SecretReference.parse(rawReference).toString(), plaintext, masterKey, ciphertext);
    assertSafe(stackTrace.toString(), plaintext, masterKey, ciphertext);
  }

  private static void assertSafe(
      String producedOutput, String plaintext, String masterKey, String ciphertext) {
    assertThat(producedOutput).doesNotContain(plaintext, masterKey, ciphertext);
  }

  private static final class CapturingHandler extends Handler {
    private final StringBuilder content = new StringBuilder();

    @Override
    public void publish(LogRecord record) {
      content.append(record.getMessage()).append('\n');
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}

    String content() {
      return content.toString();
    }
  }
}

package com.codinglair.taf.runtime.secret.tool;

import com.codinglair.taf.runtime.definition.CsvSecretNormalizationRequest;
import com.codinglair.taf.runtime.definition.CsvSecretNormalizer;
import com.codinglair.taf.runtime.definition.SecretFieldDefinition;
import com.codinglair.taf.runtime.definition.SecretKind;
import com.codinglair.taf.runtime.secret.JasyptSecretProvider;
import com.codinglair.taf.runtime.secret.JasyptSecretProvisioner;
import java.nio.file.Path;
import java.util.Map;

/** Explicit local command for authorized in-place normalization of a workspace CSV copy. */
public final class JasyptCsvSecretNormalizer {
  private JasyptCsvSecretNormalizer() {}

  public static void main(String[] arguments) {
    if (arguments.length != 2) {
      throw new IllegalArgumentException("Expected: <workspace-input.csv> <master-key-env-name>");
    }
    Path workspace = Path.of("").toAbsolutePath().normalize();
    Path source = workspace.resolve(arguments[0]).normalize();
    if (!source.startsWith(workspace) || source.getParent() == null) {
      throw new IllegalArgumentException("CSV path must remain inside the working directory");
    }
    JasyptSecretProvider provider = new JasyptSecretProvider(System::getenv, arguments[1]);
    var request =
        new CsvSecretNormalizationRequest(
            source,
            "caseId",
            Map.of("passwordReference", SecretFieldDefinition.required(SecretKind.PASSWORD)),
            "sauce-demo",
            "local",
            System.getProperty("user.name", "local-user"),
            true);
    int count = new CsvSecretNormalizer().normalize(request, new JasyptSecretProvisioner(provider));
    System.out.println("Normalized " + count + " classified secret fields");
  }
}

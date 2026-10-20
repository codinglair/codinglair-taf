package com.codinglair.taf.runtime.core.reporting.impl.allure;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Command-line entry point for deterministic consumer-evidence validation. */
public final class AllureEvidenceValidatorCli {
  private AllureEvidenceValidatorCli() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 4) {
      System.err.println(
          "Usage: <results-directory> <standalone-html> <expectations-json> <output-json>");
      System.exit(2);
    }
    var validation =
        new AllureEvidenceValidator()
            .validate(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
    Path output = Path.of(args[3]).toAbsolutePath().normalize();
    if (output.getParent() != null) Files.createDirectories(output.getParent());
    Files.writeString(output, validation.toJson(), StandardCharsets.UTF_8);
    System.out.println(validation.toJson());
    if (!validation.valid()) System.exit(1);
  }
}

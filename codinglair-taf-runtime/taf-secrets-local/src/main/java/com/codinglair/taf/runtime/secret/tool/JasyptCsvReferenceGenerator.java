package com.codinglair.taf.runtime.secret.tool;

import com.codinglair.taf.runtime.secret.EnvironmentValueSource;
import com.codinglair.taf.runtime.secret.JasyptSecretProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Deliberate local utility that replaces marked CSV payloads without echoing sensitive material.
 */
public final class JasyptCsvReferenceGenerator {
  private static final String MARKER = "secret://jasypt/GENERATE_WITH_LOCAL_UTILITY_";

  private JasyptCsvReferenceGenerator() {}

  public static void main(String[] arguments) throws IOException {
    if (arguments.length != 4) {
      throw new IllegalArgumentException(
          "Expected: <input.csv> <output.csv> <master-key-env-name> <plaintext-env-name>");
    }
    Path input = safePath(arguments[0]);
    Path output = safePath(arguments[1]);
    int replacements = generate(input, output, arguments[2], arguments[3], System::getenv);
    System.out.println("Generated " + replacements + " distinct encrypted persona references");
  }

  static int generate(
      Path input,
      Path output,
      String masterKeyEnvironmentName,
      String plaintextEnvironmentName,
      EnvironmentValueSource environment)
      throws IOException {
    if (input.equals(output)) {
      throw new IllegalArgumentException("Input and output paths must differ");
    }
    String masterKeyName = requireEnvironmentName(masterKeyEnvironmentName);
    String plaintextName = requireEnvironmentName(plaintextEnvironmentName);
    String plaintext = environment.get(plaintextName);
    if (plaintext == null || plaintext.isBlank()) {
      throw new IllegalStateException("Plaintext source environment variable is unavailable");
    }
    JasyptSecretProvider provider = new JasyptSecretProvider(environment, masterKeyName);
    provider.verifyReady();
    List<String> generated = new ArrayList<>();
    int replacements = 0;
    for (String line : Files.readAllLines(input)) {
      int marker = line.indexOf(MARKER);
      if (marker < 0) {
        generated.add(line);
        continue;
      }
      int end = line.indexOf(',', marker);
      if (end < 0) end = line.length();
      String encrypted = provider.encryptForLocalDevelopment(plaintext.toCharArray());
      generated.add(
          line.substring(0, marker) + "secret://jasypt/" + encrypted + line.substring(end));
      replacements++;
    }
    if (replacements < 2) {
      throw new IllegalStateException("Input must contain at least two marked persona references");
    }
    Files.createDirectories(output.getParent());
    Files.write(output, generated);
    return replacements;
  }

  private static Path safePath(String value) {
    Path workspace = Path.of("").toAbsolutePath().normalize();
    Path path = workspace.resolve(value).normalize();
    if (!path.startsWith(workspace) || path.getParent() == null) {
      throw new IllegalArgumentException("CSV path must remain inside the working directory");
    }
    return path;
  }

  private static String requireEnvironmentName(String value) {
    if (value == null || !value.matches("[A-Z_][A-Z0-9_]{0,127}")) {
      throw new IllegalArgumentException("Environment-variable name is invalid");
    }
    return value;
  }
}

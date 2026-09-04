package com.codinglair.taf.runtime.core.reporting.impl.allure;

import com.codinglair.taf.runtime.core.reporting.RedactionPipeline;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Invokes the supported Allure 2 command-line single-file contract. */
final class AllureCliSingleFileReportGenerator implements SingleFileReportGenerator {
  private static final int MAX_DIAGNOSTIC_CHARS = 8192;

  @Override
  public Path generate(GenerationRequest request) {
    Path log =
        request
            .stagingDirectory()
            .resolveSibling(request.stagingDirectory().getFileName() + ".log");
    List<String> command = command(request);
    Process process;
    try {
      process =
          new ProcessBuilder(command)
              .redirectErrorStream(true)
              .redirectOutput(log.toFile())
              .start();
    } catch (IOException failure) {
      throw new AllureSingleFilePublicationException(
          "generator-start",
          "could not start configured executable '"
              + request.executable()
              + "'; install the approved Allure 2 CLI or configure executable",
          failure);
    }

    boolean completed;
    try {
      completed = process.waitFor(request.timeout().toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException interrupted) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
      throw new AllureSingleFilePublicationException(
          "generator-wait", "interrupted while waiting for the Allure CLI", interrupted);
    }
    if (!completed) {
      process.destroyForcibly();
      throw new AllureSingleFilePublicationException(
          "generator-timeout",
          "Allure CLI exceeded timeout "
              + request.timeout()
              + "; increase timeout or inspect the results set");
    }
    if (process.exitValue() != 0) {
      throw new AllureSingleFilePublicationException(
          "generator-exit",
          "Allure CLI returned exit code "
              + process.exitValue()
              + diagnostic(log)
              + "; verify Allure 2 compatibility and the results directory");
    }
    return request.stagingDirectory().resolve("index.html");
  }

  private static List<String> command(GenerationRequest request) {
    List<String> allure =
        List.of(
            request.executable(),
            "generate",
            request.resultsDirectory().toString(),
            "--output",
            request.stagingDirectory().toString(),
            "--single-file",
            "--clean");
    String executable = request.executable().toLowerCase(Locale.ROOT);
    if (!isWindows() || (!executable.endsWith(".bat") && !executable.endsWith(".cmd"))) {
      return allure;
    }
    assertSafeWindowsCommand(allure);
    List<String> wrapped = new ArrayList<>();
    wrapped.add("cmd.exe");
    wrapped.add("/d");
    wrapped.add("/c");
    wrapped.addAll(allure);
    return wrapped;
  }

  private static void assertSafeWindowsCommand(List<String> command) {
    if (command.stream().anyMatch(value -> value.matches(".*[&|<>^].*"))) {
      throw new AllureSingleFilePublicationException(
          "generator-preflight", "Windows command paths must not contain shell metacharacters");
    }
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }

  private static String diagnostic(Path log) {
    try {
      String output = Files.readString(log, StandardCharsets.UTF_8);
      String bounded = output.substring(0, Math.min(output.length(), MAX_DIAGNOSTIC_CHARS));
      String sanitized = new RedactionPipeline().redact(bounded).replaceAll("\\s+", " ").trim();
      return sanitized.isEmpty() ? "" : "; sanitized diagnostic: " + sanitized;
    } catch (IOException ignored) {
      return "";
    }
  }
}

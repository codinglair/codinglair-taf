package com.codinglair.taf.mcp;

import com.codinglair.taf.mcp.tools.ScaffoldCompiler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Runs the generated project's own Maven wrapper with fixed, server-owned preflight arguments. */
final class MavenWrapperScaffoldCompiler implements ScaffoldCompiler {
  private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);
  private final Duration timeout;
  private final boolean windows;

  MavenWrapperScaffoldCompiler() {
    this(DEFAULT_TIMEOUT, System.getProperty("os.name", ""));
  }

  MavenWrapperScaffoldCompiler(Duration timeout, String osName) {
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must be positive");
    }
    this.timeout = timeout;
    this.windows = osName.toLowerCase(Locale.ROOT).contains("windows");
  }

  @Override
  public CompilationResult compile(Path workspace) {
    Path canonicalWorkspace = workspace.toAbsolutePath().normalize();
    Path wrapper = canonicalWorkspace.resolve(windows ? "mvnw.cmd" : "mvnw");
    if (!Files.isRegularFile(wrapper) || Files.isSymbolicLink(wrapper)) {
      return new CompilationResult(false, "generated Maven wrapper is unavailable");
    }

    Process process = null;
    try {
      process =
          new ProcessBuilder(command(wrapper))
              .directory(canonicalWorkspace.toFile())
              .redirectInput(ProcessBuilder.Redirect.PIPE)
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .redirectError(ProcessBuilder.Redirect.DISCARD)
              .start();
      process.getOutputStream().close();
      if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
        terminate(process);
        return new CompilationResult(false, "generated project preflight timed out");
      }
      return process.exitValue() == 0
          ? new CompilationResult(true, "generated project preflight passed")
          : new CompilationResult(false, "generated project preflight failed");
    } catch (IOException failure) {
      return new CompilationResult(false, "generated project preflight could not start");
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      if (process != null) terminate(process);
      return new CompilationResult(false, "generated project preflight was interrupted");
    }
  }

  private List<String> command(Path wrapper) {
    return windows
        ? List.of("cmd.exe", "/d", "/c", wrapper.toString(), "-B", "-ntp", "-DskipTests", "compile")
        : List.of("/bin/sh", wrapper.toString(), "-B", "-ntp", "-DskipTests", "compile");
  }

  private static void terminate(Process process) {
    process.descendants().forEach(ProcessHandle::destroyForcibly);
    process.destroyForcibly();
  }
}

package com.codinglair.taf.mcp.worker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Fixed administrator-defined argv. Request data is never interpolated into a command line. */
public record WorkerCommand(String workflow, List<String> argv) {
  public WorkerCommand {
    if (workflow == null || workflow.isBlank() || argv == null || argv.isEmpty()) {
      throw new IllegalArgumentException("Workflow and argv are required");
    }
    argv = List.copyOf(argv);
    var executable = Path.of(argv.getFirst());
    if (!executable.isAbsolute()
        || !Files.isRegularFile(executable)
        || Files.isSymbolicLink(executable)) {
      throw new IllegalArgumentException("Allowlisted executable must be an absolute regular file");
    }
    if (argv.stream()
        .anyMatch(value -> value == null || value.isBlank() || value.indexOf('\0') >= 0)) {
      throw new IllegalArgumentException("Allowlisted argv contains an invalid value");
    }
  }
}

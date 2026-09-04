package com.codinglair.taf.mcp.tools;

import java.nio.file.Path;

/**
 * Internal server-composition boundary for invoking an approved compile workflow.
 *
 * <p>This is not an MCP client extension point. Implementations must be server-owned and must not
 * accept request-controlled executables or arguments.
 */
@FunctionalInterface
public interface ScaffoldCompiler {
  /**
   * Compiles the newly created scaffold through fixed server-owned configuration.
   *
   * @param workspace confined scaffold destination
   * @return bounded success/failure outcome; never raw compiler output
   */
  CompilationResult compile(Path workspace);

  /**
   * Internal bounded outcome; workflow responses intentionally discard implementation detail.
   *
   * @param successful whether the approved compile workflow completed successfully
   * @param summary bounded internal summary, never request-controlled or raw compiler output
   */
  record CompilationResult(boolean successful, String summary) {
    /** Validates that the internal summary is present and bounded. */
    public CompilationResult {
      if (summary == null || summary.isBlank() || summary.length() > 4096) {
        throw new IllegalArgumentException("a bounded compilation summary is required");
      }
    }
  }
}

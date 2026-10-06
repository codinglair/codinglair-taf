package com.codinglair.taf.mcp.stdio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@DisplayName("STDIO protocol input")
@ResourceLock(Resources.SYSTEM_ERR)
@ResourceLock(Resources.SYSTEM_OUT)
class StdioProtocolInputTest {
  private PrintStream originalError;
  private PrintStream originalOutput;
  private ByteArrayOutputStream errors;
  private ByteArrayOutputStream output;

  @BeforeEach
  void captureProcessStreams() {
    originalError = System.err;
    originalOutput = System.out;
    errors = new ByteArrayOutputStream();
    output = new ByteArrayOutputStream();
    System.setErr(new PrintStream(errors, true, StandardCharsets.UTF_8));
    System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
  }

  @AfterEach
  void restoreProcessStreams() {
    System.setErr(originalError);
    System.setOut(originalOutput);
  }

  @Nested
  @DisplayName("frame validation")
  class FrameValidation {
    @Test
    @DisplayName("normalizes a valid final frame with no source newline")
    void normalizesFinalFrameNewline() throws Exception {
      var input = protocolInput("{\"jsonrpc\":\"2.0\"}");

      assertThat(input.readAllBytes())
          .asString(StandardCharsets.UTF_8)
          .isEqualTo("{\"jsonrpc\":\"2.0\"}\n");
      assertThat(errors.size()).isZero();
    }

    @Test
    @DisplayName("drains an oversized frame before returning the following valid frame")
    void drainsOversizedFrame() throws Exception {
      byte[] oversized =
          "x".repeat(StdioProtocolInput.MAXIMUM_FRAME_BYTES + 1).getBytes(StandardCharsets.UTF_8);
      var source = new ByteArrayOutputStream();
      source.writeBytes(oversized);
      source.write('\n');
      source.writeBytes("{\"id\":1}\n".getBytes(StandardCharsets.UTF_8));
      var input = new StdioProtocolInput(new ByteArrayInputStream(source.toByteArray()));

      assertThat(input.readAllBytes()).asString(StandardCharsets.UTF_8).isEqualTo("{\"id\":1}\n");
      assertThat(errors.toString(StandardCharsets.UTF_8))
          .isEqualTo("Rejected malformed or oversized MCP STDIO frame" + System.lineSeparator());
      assertThat(output.size()).isZero();
    }

    @Test
    @DisplayName("reports a malformed final frame and then returns end of input")
    void rejectsMalformedFrameAtEndOfInput() throws Exception {
      var input = protocolInput("not-json");

      assertThat(input.read()).isEqualTo(-1);
      assertThat(errors.toString(StandardCharsets.UTF_8))
          .isEqualTo("Rejected malformed or oversized MCP STDIO frame" + System.lineSeparator());
      assertThat(output.size()).isZero();
    }
  }

  private static StdioProtocolInput protocolInput(String source) {
    return new StdioProtocolInput(
        new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));
  }
}

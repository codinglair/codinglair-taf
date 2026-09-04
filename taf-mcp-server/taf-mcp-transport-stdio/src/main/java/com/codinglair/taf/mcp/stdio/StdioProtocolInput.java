package com.codinglair.taf.mcp.stdio;

import io.modelcontextprotocol.json.McpJsonDefaults;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Bounds and validates newline-delimited JSON before it reaches Spring AI's STDIO session. */
public final class StdioProtocolInput extends InputStream {
  static final int MAXIMUM_FRAME_BYTES = 1024 * 1024;
  private final InputStream source;
  private ByteArrayInputStream frame = new ByteArrayInputStream(new byte[0]);

  StdioProtocolInput(InputStream source) {
    this.source = source;
  }

  public static void install() {
    if (!(System.in instanceof StdioProtocolInput)) {
      System.setIn(new StdioProtocolInput(System.in));
    }
  }

  @Override
  public int read() throws IOException {
    int value = frame.read();
    while (value == -1) {
      byte[] next = nextValidFrame();
      if (next == null) {
        return -1;
      }
      frame = new ByteArrayInputStream(next);
      value = frame.read();
    }
    return value;
  }

  @Override
  public int read(byte[] target, int offset, int length) throws IOException {
    if (length == 0) {
      return 0;
    }
    if (frame.available() == 0) {
      byte[] next = nextValidFrame();
      if (next == null) {
        return -1;
      }
      frame = new ByteArrayInputStream(next);
    }
    return frame.read(target, offset, length);
  }

  private byte[] nextValidFrame() throws IOException {
    while (true) {
      var buffer = new ByteArrayOutputStream();
      boolean oversized = false;
      int value;
      while ((value = source.read()) != -1 && value != '\n') {
        if (buffer.size() < MAXIMUM_FRAME_BYTES) {
          buffer.write(value);
        } else {
          oversized = true;
        }
      }
      if (value == -1 && buffer.size() == 0) {
        return null;
      }
      if (!oversized && validJson(buffer.toByteArray())) {
        buffer.write('\n');
        return buffer.toByteArray();
      }
      System.err.println("Rejected malformed or oversized MCP STDIO frame");
      if (value == -1) {
        return null;
      }
    }
  }

  private static boolean validJson(byte[] value) {
    if (value.length == 0) {
      return false;
    }
    try {
      Object parsed =
          McpJsonDefaults.getMapper()
              .readValue(new String(value, StandardCharsets.UTF_8), Object.class);
      return parsed instanceof java.util.Map<?, ?>;
    } catch (IOException | RuntimeException malformed) {
      return false;
    }
  }
}

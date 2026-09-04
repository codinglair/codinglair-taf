package com.codinglair.taf.mcp.worker;

import com.codinglair.taf.mcp.security.ResponseRedactor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class BoundedOutput {
  private final byte[] bytes;
  private int size;
  private volatile boolean truncated;

  BoundedOutput(long maximumBytes) {
    if (maximumBytes > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Output byte limit is too large");
    }
    bytes = new byte[(int) maximumBytes];
  }

  void drain(InputStream input) throws IOException {
    var buffer = new byte[8192];
    int read;
    while ((read = input.read(buffer)) >= 0) {
      synchronized (this) {
        var accepted = Math.min(read, bytes.length - size);
        if (accepted > 0) {
          System.arraycopy(buffer, 0, bytes, size, accepted);
          size += accepted;
        }
        truncated |= accepted < read;
      }
    }
  }

  synchronized String sanitized(ResponseRedactor redactor) {
    return (String) redactor.redact(new String(bytes, 0, size, StandardCharsets.UTF_8));
  }

  boolean truncated() {
    return truncated;
  }
}

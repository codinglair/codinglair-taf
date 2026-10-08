package com.codinglair.taf.runtime.core.security;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Enforces byte and time limits even after streaming response headers have arrived. */
public final class BoundedHttpBody {
  private BoundedHttpBody() {}

  public static byte[] read(InputStream input, int maximumBytes, Duration timeout)
      throws IOException, InterruptedException {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var read = executor.submit(() -> input.readNBytes(maximumBytes + 1));
      try {
        byte[] bytes = read.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        if (bytes.length > maximumBytes) throw new IOException("HTTP response limit exceeded");
        return bytes;
      } catch (ExecutionException | TimeoutException _) {
        throw new IOException("HTTP response failed or exceeded its deadline");
      } finally {
        if (!read.isDone()) {
          input.close();
          read.cancel(true);
        }
      }
    }
  }
}

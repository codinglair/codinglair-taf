package com.codinglair.taf.runtime.environment;

import java.util.function.Consumer;
import org.testcontainers.containers.output.OutputFrame;

final class BoundedContainerLog implements Consumer<OutputFrame> {
  private final int capacity;
  private final StringBuilder content = new StringBuilder();

  BoundedContainerLog(int capacity) {
    this.capacity = capacity;
  }

  @Override
  public synchronized void accept(OutputFrame frame) {
    String text = DiagnosticSanitizer.sanitize(frame == null ? "" : frame.getUtf8String());
    content.append(text);
    if (content.length() > capacity) {
      content.delete(0, content.length() - capacity);
    }
  }

  synchronized String snapshot() {
    return content.toString();
  }
}

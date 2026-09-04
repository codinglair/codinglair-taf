package com.codinglair.taf.runtime.definition;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Validated, bounded payload stream. The caller must close it. */
public final class ResolvedPayload implements AutoCloseable {
  private final PayloadEvidence evidence;
  private final PayloadRange range;
  private final InputStream stream;
  private final Runnable cleanup;
  private final AtomicBoolean closed = new AtomicBoolean();

  public ResolvedPayload(
      PayloadEvidence evidence, PayloadRange range, InputStream stream, Runnable cleanup) {
    this.evidence = Objects.requireNonNull(evidence);
    this.range = Objects.requireNonNull(range);
    this.stream = Objects.requireNonNull(stream);
    this.cleanup = Objects.requireNonNull(cleanup);
  }

  public PayloadEvidence evidence() {
    return evidence;
  }

  public PayloadRange range() {
    return range;
  }

  public InputStream stream() {
    return stream;
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) return;
    try {
      stream.close();
    } catch (IOException _) {
      /* cleanup still must run */
    }
    cleanup.run();
  }
}

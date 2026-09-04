package com.codinglair.taf.runtime.core.reporting;

import java.util.Objects;
import java.util.Optional;

/** Invocation-local neutral reporting boundary owned by a runner. */
public final class CurrentReportingContext {
  private final ThreadLocal<ReportingContext> current = new ThreadLocal<>();

  public void bind(ReportingContext context) {
    Objects.requireNonNull(context, "context");
    if (current.get() != null) {
      throw new IllegalStateException("A reporting context is already bound to this invocation");
    }
    current.set(context);
  }

  public Optional<ReportingContext> current() {
    return Optional.ofNullable(current.get());
  }

  public ReportingContext require() {
    return current()
        .orElseThrow(
            () -> new IllegalStateException("No reporting context is bound to this invocation"));
  }

  public void unbind() {
    current.remove();
  }
}

package com.codinglair.taf.runtime.core.reporting;

import com.codinglair.taf.runtime.core.reporting.abstraction.ReportEvent;
import com.codinglair.taf.runtime.core.reporting.abstraction.ReportLevel;
import com.codinglair.taf.runtime.core.reporting.abstraction.ValidationContext;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;

/** One runner invocation's neutral hierarchy and reporter dispatch cursor. */
public final class ReportingContext {
  private final HierarchicalReport hierarchy;
  private final List<ReporterDispatcher> dispatchers;
  private int flushed;

  public ReportingContext(HierarchicalReport hierarchy, List<ReporterDispatcher> dispatchers) {
    this.hierarchy = Objects.requireNonNull(hierarchy, "hierarchy");
    this.dispatchers = List.copyOf(dispatchers);
  }

  public HierarchicalReport.Scope open(ReportLevel level, String name) {
    return hierarchy.open(level, name);
  }

  public <T> T execute(ReportLevel level, String name, Callable<T> action) throws Exception {
    Objects.requireNonNull(action, "action");
    try (HierarchicalReport.Scope scope = hierarchy.open(level, name)) {
      try {
        T result = action.call();
        scope.pass();
        return result;
      } catch (Throwable failure) {
        scope.fail(message(failure));
        throwAny(failure);
        return null;
      } finally {
        flush();
      }
    }
  }

  public void validation(String name, String status, Object expected, Object actual) {
    hierarchy.validation(
        UUID.randomUUID().toString(),
        name,
        status,
        new ValidationContext(String.valueOf(expected), String.valueOf(actual), Map.of()));
    flush();
  }

  public synchronized void flush() {
    List<ReportEvent> events = hierarchy.events();
    for (; flushed < events.size(); flushed++) {
      ReportEvent event = events.get(flushed);
      dispatchers.forEach(dispatcher -> dispatcher.reportEvent(event));
    }
  }

  public List<ReportEvent> events() {
    return hierarchy.events();
  }

  private static String message(Throwable failure) {
    return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void throwAny(Throwable failure) throws E {
    throw (E) failure;
  }
}

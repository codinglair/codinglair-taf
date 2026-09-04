package com.codinglair.taf.runtime.core.reporting;

import com.codinglair.taf.runtime.core.reporting.abstraction.ReportEvent;
import com.codinglair.taf.runtime.core.reporting.abstraction.ReportLevel;
import com.codinglair.taf.runtime.core.reporting.abstraction.ValidationContext;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Thread-safe neutral hierarchy with exactly-once start/finish suppression. */
public final class HierarchicalReport {
  private final String correlationId;
  private final RedactionService redaction;
  private final CopyOnWriteArrayList<ReportEvent> events = new CopyOnWriteArrayList<>();
  private final ConcurrentHashMap<String, ReportEvent.Phase> states = new ConcurrentHashMap<>();
  private final ThreadLocal<Deque<String>> parents = ThreadLocal.withInitial(ArrayDeque::new);

  public HierarchicalReport(String correlationId, RedactionService redaction) {
    if (correlationId == null || correlationId.isBlank())
      throw new IllegalArgumentException("correlationId must not be blank");
    this.correlationId = correlationId;
    this.redaction = Objects.requireNonNull(redaction, "redaction");
  }

  public Scope open(ReportLevel level, String name) {
    return open(UUID.randomUUID().toString(), level, name, null);
  }

  public Scope open(String eventId, ReportLevel level, String name, String description) {
    String parent = parents.get().peek();
    boolean owned = states.putIfAbsent(eventId, ReportEvent.Phase.STARTED) == null;
    if (owned) {
      events.add(
          event(
              eventId,
              parent,
              level,
              ReportEvent.Phase.STARTED,
              name,
              null,
              description,
              Map.of()));
      parents.get().push(eventId);
    }
    return new Scope(eventId, parent, level, name, description, owned);
  }

  public void validation(String eventId, String name, String status, ValidationContext validation) {
    Objects.requireNonNull(validation, "validation");
    Map<String, String> context = new java.util.LinkedHashMap<>();
    context.put("expected", safe(validation.expected()));
    context.put("actual", safe(validation.actual()));
    validation.metadata().forEach((key, value) -> context.put(safe(key), safe(value)));
    finishSingle(eventId, ReportLevel.VALIDATION, name, status, null, context);
  }

  public List<ReportEvent> events() {
    return List.copyOf(events);
  }

  private void finishSingle(
      String id,
      ReportLevel level,
      String name,
      String status,
      String description,
      Map<String, String> context) {
    if (states.putIfAbsent(id, ReportEvent.Phase.FINISHED) == null) {
      String parent = parents.get().peek();
      events.add(
          event(id, parent, level, ReportEvent.Phase.STARTED, name, null, description, Map.of()));
      events.add(
          event(id, parent, level, ReportEvent.Phase.FINISHED, name, status, description, context));
    }
  }

  private ReportEvent event(
      String id,
      String parent,
      ReportLevel level,
      ReportEvent.Phase phase,
      String name,
      String status,
      String description,
      Map<String, String> context) {
    return new ReportEvent(
        id,
        parent,
        correlationId,
        level,
        phase,
        safe(name),
        safe(status),
        safe(description),
        context,
        Instant.now());
  }

  private String safe(String value) {
    return value == null ? null : redaction.redact(value);
  }

  public final class Scope implements AutoCloseable {
    private final String id;
    private final String parent;
    private final ReportLevel level;
    private final String name;
    private final String description;
    private final boolean owned;
    private boolean closed;

    private Scope(
        String id,
        String parent,
        ReportLevel level,
        String name,
        String description,
        boolean owned) {
      this.id = id;
      this.parent = parent;
      this.level = level;
      this.name = name;
      this.description = description;
      this.owned = owned;
    }

    public void pass() {
      finish("PASSED", Map.of());
    }

    public void fail(String failure) {
      finish("FAILED", Map.of("failure", safe(failure)));
    }

    @Override
    public void close() {
      pass();
    }

    private void finish(String status, Map<String, String> context) {
      if (closed || !owned) return;
      closed = true;
      Deque<String> stack = parents.get();
      if (id.equals(stack.peek())) stack.pop();
      else stack.remove(id);
      if (stack.isEmpty()) parents.remove();
      if (states.replace(id, ReportEvent.Phase.STARTED, ReportEvent.Phase.FINISHED)) {
        events.add(
            event(
                id, parent, level, ReportEvent.Phase.FINISHED, name, status, description, context));
      }
    }
  }
}

package com.codinglair.taf.mcp.security;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class InMemoryAuditLog implements AuditLog {
    private final List<AuditEvent> events = new ArrayList<>();
    private final Clock clock;
    private final ResponseRedactor redactor;

    public InMemoryAuditLog(Clock clock, ResponseRedactor redactor) {
        this.clock = clock;
        this.redactor = redactor;
    }

    @Override
    public synchronized AuditEvent append(
            String correlationId, String type, String actorId, Map<String, Object> details) {
        @SuppressWarnings("unchecked")
        var sanitized = (Map<String, Object>) redactor.redact(details);
        var event = new AuditEvent(events.size(), clock.instant(), correlationId, type, actorId, sanitized);
        events.add(event);
        return event;
    }

    @Override
    public synchronized List<AuditEvent> events() {
        return List.copyOf(events);
    }
}

package com.codinglair.taf.mcp.security;

import java.time.Instant;
import java.util.Map;

public record AuditEvent(
        long sequence,
        Instant occurredAt,
        String correlationId,
        String type,
        String actorId,
        Map<String, Object> details) {
    public AuditEvent {
        if (sequence < 0 || occurredAt == null || correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("valid audit identity fields are required");
        }
        if (type == null || type.isBlank() || actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("audit type and actor are required");
        }
        details = Map.copyOf(details);
    }
}

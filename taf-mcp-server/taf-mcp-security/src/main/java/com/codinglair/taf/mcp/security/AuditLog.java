package com.codinglair.taf.mcp.security;

import java.util.List;
import java.util.Map;

public interface AuditLog {
    AuditEvent append(String correlationId, String type, String actorId, Map<String, Object> details);

    List<AuditEvent> events();
}

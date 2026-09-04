package com.codinglair.taf.mcp.security;

import java.time.Instant;

public record ApprovalEvent(long sequence, ApprovalStatus status, String actorId, Instant occurredAt) {
    public ApprovalEvent {
        if (sequence < 0 || status == null || actorId == null || actorId.isBlank() || occurredAt == null) {
            throw new IllegalArgumentException("valid approval event fields are required");
        }
    }
}

package com.codinglair.taf.mcp.security;

import java.time.Instant;
import java.util.List;

public record ApprovalRequest(
        String id,
        String bindingDigest,
        String requesterId,
        ApprovalStatus status,
        Instant expiresAt,
        List<ApprovalEvent> history) {
    public ApprovalRequest {
        if (id == null || id.isBlank() || bindingDigest == null || bindingDigest.isBlank()) {
            throw new IllegalArgumentException("approval id and binding digest are required");
        }
        if (requesterId == null || requesterId.isBlank() || status == null || expiresAt == null) {
            throw new IllegalArgumentException("approval requester, status, and expiry are required");
        }
        history = List.copyOf(history);
        if (history.isEmpty() || history.getLast().status() != status) {
            throw new IllegalArgumentException("approval history must end in the current status");
        }
    }
}

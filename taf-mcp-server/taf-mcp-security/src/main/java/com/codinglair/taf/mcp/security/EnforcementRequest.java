package com.codinglair.taf.mcp.security;

public record EnforcementRequest(
        String correlationId,
        Transport transport,
        AuthorizationContext authorization,
        String operationDigest,
        String approvalId,
        Object input) {
    public EnforcementRequest {
        if (correlationId == null || correlationId.isBlank() || transport == null || authorization == null) {
            throw new IllegalArgumentException("correlation, transport, and authorization are required");
        }
        if (operationDigest == null || operationDigest.isBlank()) {
            throw new IllegalArgumentException("operation digest is required");
        }
    }

    public EnforcementRequest(
            String correlationId,
            Transport transport,
            AuthorizationContext authorization,
            String operationDigest,
            String approvalId) {
        this(correlationId, transport, authorization, operationDigest, approvalId, java.util.Map.of());
    }
}

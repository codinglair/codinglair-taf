package com.codinglair.taf.mcp.security;

public record EnforcementResult(Status status, Object body) {
    public enum Status {
        ALLOWED,
        DENIED,
        APPROVAL_REQUIRED,
        INPUT_REJECTED,
        FAILED
    }
}

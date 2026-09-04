package com.codinglair.taf.mcp.security;

import java.util.Set;

public record CallerIdentity(String userId, Set<String> roles, String agentId, IdentityKind kind) {
    public CallerIdentity {
        userId = requireText(userId, "userId");
        roles = Set.copyOf(roles);
        if (roles.isEmpty() || roles.stream().anyMatch(role -> role == null || role.isBlank())) {
            throw new IllegalArgumentException("roles must contain non-blank values");
        }
        agentId = requireText(agentId, "agentId");
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}

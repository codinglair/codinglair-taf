package com.codinglair.taf.mcp.security;

public record AuthorizationContext(
        CallerIdentity identity,
        String project,
        String environment,
        String action,
        String permission) {
    public AuthorizationContext {
        if (identity == null) {
            throw new IllegalArgumentException("identity is required");
        }
        project = requireText(project, "project");
        environment = requireText(environment, "environment");
        action = requireText(action, "action");
        permission = requireText(permission, "permission");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}

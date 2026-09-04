package com.codinglair.taf.mcp.security;

import java.util.Set;

public record PolicyRule(
        String id,
        Effect effect,
        Set<String> users,
        Set<String> roles,
        Set<String> projects,
        Set<String> environments,
        Set<String> actions,
        Set<String> agents,
        Set<String> permissions,
        boolean approvalRequired) {
    public enum Effect {
        ALLOW,
        DENY
    }

    public PolicyRule {
        if (id == null || id.isBlank() || effect == null) {
            throw new IllegalArgumentException("rule id and effect are required");
        }
        users = values(users);
        roles = values(roles);
        projects = values(projects);
        environments = values(environments);
        actions = values(actions);
        agents = values(agents);
        permissions = values(permissions);
    }

    boolean appliesTo(AuthorizationContext context) {
        return matches(users, context.identity().userId())
                && context.identity().roles().stream().anyMatch(role -> matches(roles, role))
                && matches(projects, context.project())
                && matches(environments, context.environment())
                && matches(actions, context.action())
                && matches(agents, context.identity().agentId())
                && matches(permissions, context.permission());
    }

    private static Set<String> values(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of("*");
        }
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("policy selectors must be non-blank");
        }
        return Set.copyOf(values);
    }

    private static boolean matches(Set<String> selectors, String value) {
        return selectors.contains("*") || selectors.contains(value);
    }
}

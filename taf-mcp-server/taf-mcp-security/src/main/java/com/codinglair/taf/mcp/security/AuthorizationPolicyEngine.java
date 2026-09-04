package com.codinglair.taf.mcp.security;

import java.util.List;
import java.util.Set;

public final class AuthorizationPolicyEngine {
    private final List<PolicyRule> rules;
    private final Set<String> consequentialActions;

    public AuthorizationPolicyEngine(List<PolicyRule> rules) {
        this(rules, Set.of());
    }

    public AuthorizationPolicyEngine(List<PolicyRule> rules, Set<String> consequentialActions) {
        this.rules = List.copyOf(rules);
        var configured = new java.util.HashSet<>(consequentialActions);
        configured.add("scaffold");
        this.consequentialActions = Set.copyOf(configured);
    }

    public AuthorizationDecision decide(AuthorizationContext context) {
        var applicable = rules.stream().filter(rule -> rule.appliesTo(context)).toList();
        if (applicable.isEmpty()) {
            return new AuthorizationDecision(false, false, List.of());
        }
        var denied = applicable.stream().anyMatch(rule -> rule.effect() == PolicyRule.Effect.DENY);
        var allowed = applicable.stream().anyMatch(rule -> rule.effect() == PolicyRule.Effect.ALLOW);
        var approval =
                consequentialActions.contains(context.action())
                        || applicable.stream().anyMatch(PolicyRule::approvalRequired);
        return new AuthorizationDecision(
                allowed && !denied,
                approval,
                applicable.stream().map(PolicyRule::id).sorted().toList());
    }
}

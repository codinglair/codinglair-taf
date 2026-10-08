package com.codinglair.taf.mcp.security;

import com.codinglair.taf.runtime.core.security.ResourceAccess;
import java.util.List;
import java.util.Set;

public final class AuthorizationPolicyEngine {
  private final List<PolicyRule> rules;
  private final Set<String> consequentialActions;
  private final List<ResourcePolicyRule> resourceRules;

  public AuthorizationPolicyEngine(List<PolicyRule> rules) {
    this(rules, Set.of());
  }

  public AuthorizationPolicyEngine(List<PolicyRule> rules, Set<String> consequentialActions) {
    this(rules, consequentialActions, List.of());
  }

  public AuthorizationPolicyEngine(
      List<PolicyRule> rules,
      Set<String> consequentialActions,
      List<ResourcePolicyRule> resourceRules) {
    this.rules = List.copyOf(rules);
    this.resourceRules = List.copyOf(resourceRules);
    var configured = new java.util.HashSet<>(consequentialActions);
    configured.add("scaffold");
    this.consequentialActions = Set.copyOf(configured);
  }

  /** Legacy action-only rules do not implicitly grant access to arbitrary resource identifiers. */
  public AuthorizationDecision decideResource(
      AuthorizationContext context, ResourceAccess resource) {
    var action = decide(context);
    var applicable =
        resourceRules.stream().filter(rule -> rule.appliesTo(context, resource)).toList();
    boolean denied =
        applicable.stream().anyMatch(rule -> rule.scope().effect() == PolicyRule.Effect.DENY);
    boolean allowed =
        applicable.stream().anyMatch(rule -> rule.scope().effect() == PolicyRule.Effect.ALLOW);
    return new AuthorizationDecision(
        action.allowed() && allowed && !denied,
        action.approvalRequired()
            || applicable.stream().anyMatch(rule -> rule.scope().approvalRequired()),
        applicable.stream().map(rule -> rule.scope().id()).sorted().toList());
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
        allowed && !denied, approval, applicable.stream().map(PolicyRule::id).sorted().toList());
  }
}

package com.codinglair.taf.mcp.security;

import java.util.List;

public record AuthorizationDecision(boolean allowed, boolean approvalRequired, List<String> ruleIds) {
    public AuthorizationDecision {
        ruleIds = List.copyOf(ruleIds);
    }
}

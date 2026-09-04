package com.codinglair.taf.mcp.http;

import com.codinglair.taf.mcp.prompts.McpPromptReportService;
import com.codinglair.taf.mcp.prompts.PromptCatalog;
import com.codinglair.taf.mcp.security.ApprovalService;
import com.codinglair.taf.mcp.security.AuthorizationPolicyEngine;
import com.codinglair.taf.mcp.security.InMemoryAuditLog;
import com.codinglair.taf.mcp.security.McpEnforcementService;
import com.codinglair.taf.mcp.security.PolicyRule;
import com.codinglair.taf.mcp.security.ResponseRedactor;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Opt-in prompt composition scoped to the disposable Kind reference deployment. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "taf.mcp.kind-reference", name = "enabled", havingValue = "true")
class KindReferencePromptConfiguration {
  @Bean
  @ConditionalOnMissingBean
  McpPromptReportService kindReferencePromptReportService() {
    Clock clock = Clock.systemUTC();
    var redactor = new ResponseRedactor(Set.of());
    var policy =
        new AuthorizationPolicyEngine(
            List.of(
                new PolicyRule(
                    "kind-reference-prompt-read",
                    PolicyRule.Effect.ALLOW,
                    Set.of("*"),
                    Set.of("SCOPE_taf.prompts.read"),
                    Set.of("kind-smoke"),
                    Set.of("kind"),
                    Set.of("prompts.list", "prompts.get"),
                    Set.of("*"),
                    Set.of(McpPromptReportService.REPORT_READ),
                    false)));
    var enforcement =
        new McpEnforcementService(
            policy, new ApprovalService(clock), new InMemoryAuditLog(clock, redactor), redactor);
    return new McpPromptReportService(PromptCatalog.standard(), ignored -> List.of(), enforcement);
  }
}

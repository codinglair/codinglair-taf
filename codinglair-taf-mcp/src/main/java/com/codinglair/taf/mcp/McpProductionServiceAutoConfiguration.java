package com.codinglair.taf.mcp;

import com.codinglair.taf.core.Capability;
import com.codinglair.taf.mcp.jobs.JobRepository;
import com.codinglair.taf.mcp.jobs.JobService;
import com.codinglair.taf.mcp.jobs.LocalJobRepository;
import com.codinglair.taf.mcp.prompts.McpPromptReportService;
import com.codinglair.taf.mcp.prompts.PromptCatalog;
import com.codinglair.taf.mcp.prompts.ReportRepository;
import com.codinglair.taf.mcp.resources.EvidenceMetadataRepository;
import com.codinglair.taf.mcp.resources.McpContractDocumentation;
import com.codinglair.taf.mcp.resources.McpResourceService;
import com.codinglair.taf.mcp.resources.RuntimeCapabilityCatalog;
import com.codinglair.taf.mcp.security.ApprovalService;
import com.codinglair.taf.mcp.security.AuthorizationPolicyEngine;
import com.codinglair.taf.mcp.security.InMemoryAuditLog;
import com.codinglair.taf.mcp.security.McpEnforcementService;
import com.codinglair.taf.mcp.security.ResponseRedactor;
import com.codinglair.taf.mcp.tools.McpWorkflowTools;
import com.codinglair.taf.mcp.tools.ScaffoldCompiler;
import com.codinglair.taf.mcp.tools.WorkflowOutcome;
import com.codinglair.taf.mcp.tools.WorkflowResult;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Shared fail-closed production composition used by both MCP transports. */
@AutoConfiguration
@EnableConfigurationProperties(McpProductionProperties.class)
@ConditionalOnProperty(
    prefix = "taf.mcp.application",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class McpProductionServiceAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  Clock mcpClock() {
    return Clock.systemUTC();
  }

  @Bean
  @ConditionalOnMissingBean
  ResponseRedactor mcpResponseRedactor() {
    return new ResponseRedactor(Set.of());
  }

  @Bean
  @ConditionalOnMissingBean
  AuthorizationPolicyEngine mcpAuthorizationPolicyEngine() {
    return new AuthorizationPolicyEngine(List.of());
  }

  @Bean
  @ConditionalOnMissingBean
  ApprovalService mcpApprovalService(Clock clock) {
    return new ApprovalService(clock);
  }

  @Bean
  @ConditionalOnMissingBean
  McpEnforcementService mcpEnforcementService(
      AuthorizationPolicyEngine policy,
      ApprovalService approvals,
      Clock clock,
      ResponseRedactor redactor) {
    return new McpEnforcementService(
        policy, approvals, new InMemoryAuditLog(clock, redactor), redactor);
  }

  @Bean
  @ConditionalOnMissingBean
  JobRepository mcpJobRepository(McpProductionProperties properties) {
    return new LocalJobRepository(properties.getStateRoot().resolve("jobs"));
  }

  @Bean
  @ConditionalOnMissingBean
  JobService mcpJobService(JobRepository repository, Clock clock) {
    return new JobService(repository, clock);
  }

  @Bean(destroyMethod = "shutdownNow")
  @ConditionalOnMissingBean(name = "mcpWorkflowExecutor")
  ExecutorService mcpWorkflowExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }

  @Bean
  @ConditionalOnMissingBean
  McpWorkflowTools mcpWorkflowTools(
      McpProductionProperties properties,
      McpEnforcementService enforcement,
      JobService jobs,
      JobRepository repository,
      ExecutorService mcpWorkflowExecutor,
      Clock clock,
      ResponseRedactor redactor) {
    return new McpWorkflowTools(
        properties.getWorkspaceRoot(),
        enforcement,
        jobs,
        repository,
        _ ->
            new WorkflowResult(
                WorkflowOutcome.ENVIRONMENT_FAILED, "validator is not configured", List.of()),
        (_, _) ->
            new WorkflowResult(
                WorkflowOutcome.ENVIRONMENT_FAILED, "worker is not configured", List.of()),
        mcpWorkflowExecutor,
        clock,
        redactor);
  }

  @Bean
  @ConditionalOnMissingBean
  ScaffoldCompiler mcpScaffoldCompiler() {
    return _ -> new ScaffoldCompiler.CompilationResult(false, "compiler is not configured");
  }

  @Bean
  @ConditionalOnMissingBean
  McpResourceService mcpResourceService(AuthorizationPolicyEngine policy) {
    var capabilities =
        List.of(capability("aws.eventbridge"), capability("aws.sqs"), capability("mobile.apple"));
    var versions = Map.of("aws.eventbridge", "1.1.0", "aws.sqs", "1.1.0", "mobile.apple", "1.3.0");
    return new McpResourceService(
        new RuntimeCapabilityCatalog(capabilities, versions, "1.3.1"),
        McpContractDocumentation.load(getClass().getClassLoader()),
        emptyEvidence(),
        policy,
        List.of(),
        List.of());
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "taf.mcp.kind-reference",
      name = "enabled",
      havingValue = "false",
      matchIfMissing = true)
  McpPromptReportService mcpPromptReportService(McpEnforcementService enforcement) {
    ReportRepository reports = _ -> List.of();
    return new McpPromptReportService(PromptCatalog.standard(), reports, enforcement);
  }

  private static Capability capability(String name) {
    return new Capability(name, name, Capability.CapabilityType.CONTROLLER);
  }

  private static EvidenceMetadataRepository emptyEvidence() {
    return _ -> List.of();
  }
}

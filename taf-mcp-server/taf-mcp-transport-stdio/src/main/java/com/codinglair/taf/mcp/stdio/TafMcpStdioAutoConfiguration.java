package com.codinglair.taf.mcp.stdio;

import com.codinglair.taf.mcp.McpProductionServiceAutoConfiguration;
import com.codinglair.taf.mcp.McpToolCatalogIntegrity;
import com.codinglair.taf.mcp.jobs.JobRepository;
import com.codinglair.taf.mcp.jobs.JobService;
import com.codinglair.taf.mcp.prompts.McpPromptReportService;
import com.codinglair.taf.mcp.resources.McpResourceService;
import com.codinglair.taf.mcp.security.ApprovalService;
import com.codinglair.taf.mcp.security.McpEnforcementService;
import com.codinglair.taf.mcp.security.PolicyRule;
import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine;
import com.codinglair.taf.mcp.tools.BlueprintScaffoldingService;
import com.codinglair.taf.mcp.tools.McpWorkflowTools;
import com.codinglair.taf.mcp.tools.ScaffoldCompiler;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Conditional composition of Spring AI's STDIO transport over shared TAF services. */
@AutoConfiguration
@AutoConfigureAfter(McpProductionServiceAutoConfiguration.class)
@EnableConfigurationProperties(TafMcpStdioProperties.class)
@ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "stdio", havingValue = "true")
@ConditionalOnProperty(
    prefix = "taf.mcp.stdio",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class TafMcpStdioAutoConfiguration {
  @Bean
  @ConditionalOnProperty(
      prefix = "taf.mcp.stdio",
      name = "scaffold-authorized",
      havingValue = "true")
  PolicyRule tafMcpStdioScaffoldWritePolicy(TafMcpStdioProperties properties) {
    return localScaffoldPolicy(
        "stdio-local-scaffold-write", "scaffold", "taf:working-tree:write", properties);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "taf.mcp.stdio",
      name = "scaffold-authorized",
      havingValue = "true")
  PolicyRule tafMcpStdioDependencyWritePolicy(TafMcpStdioProperties properties) {
    return localScaffoldPolicy(
        "stdio-local-dependency-write", "dependency.add", "taf:dependency:write", properties);
  }

  @Bean
  @ConditionalOnMissingBean(name = "tafMcpToolCatalogIntegrity")
  Set<String> tafMcpToolCatalogIntegrity(
      StdioWorkflowTools workflows, StdioScaffoldingTools scaffolding, StdioPromptReports prompts) {
    return McpToolCatalogIntegrity.requireComplete(List.of(workflows, scaffolding, prompts));
  }

  @Bean
  @ConditionalOnBean({McpEnforcementService.class, ApprovalService.class, ScaffoldCompiler.class})
  @ConditionalOnMissingBean
  BlueprintScaffoldingService blueprintScaffoldingService(
      TafMcpStdioProperties properties,
      McpEnforcementService enforcement,
      ApprovalService approvals,
      ScaffoldCompiler preflight) {
    return new BlueprintScaffoldingService(
        properties.getWorkspaceRoot(),
        new BlueprintCompositionEngine(),
        enforcement,
        approvals,
        preflight);
  }

  @Bean
  @ConditionalOnBean(BlueprintScaffoldingService.class)
  @ConditionalOnMissingBean
  StdioScaffoldingTools stdioScaffoldingTools(
      BlueprintScaffoldingService scaffolding, TafMcpStdioProperties properties) {
    return new StdioScaffoldingTools(scaffolding, properties);
  }

  @Bean
  @ConditionalOnBean(McpWorkflowTools.class)
  @ConditionalOnMissingBean
  StdioWorkflowTools stdioWorkflowTools(
      McpWorkflowTools workflows, TafMcpStdioProperties properties) {
    return new StdioWorkflowTools(workflows, properties);
  }

  @Bean
  @ConditionalOnBean(McpResourceService.class)
  @ConditionalOnMissingBean
  StdioResources stdioResources(McpResourceService resources, TafMcpStdioProperties properties) {
    return new StdioResources(resources, properties);
  }

  @Bean
  @ConditionalOnBean(McpPromptReportService.class)
  @ConditionalOnMissingBean
  StdioPromptReports stdioPromptReports(
      McpPromptReportService prompts, TafMcpStdioProperties properties) {
    return new StdioPromptReports(prompts, properties);
  }

  @Bean
  @ConditionalOnBean({McpWorkflowTools.class, JobService.class, JobRepository.class})
  @ConditionalOnMissingBean
  StdioShutdownCoordinator stdioShutdownCoordinator(
      McpWorkflowTools workflows, JobService jobs, JobRepository repository) {
    return new StdioShutdownCoordinator(workflows, jobs, repository, Clock.systemUTC());
  }

  private static PolicyRule localScaffoldPolicy(
      String id, String action, String permission, TafMcpStdioProperties properties) {
    return new PolicyRule(
        id,
        PolicyRule.Effect.ALLOW,
        Set.of(properties.getUserId()),
        properties.getRoles(),
        Set.of(properties.getProject()),
        Set.of(properties.getEnvironment()),
        Set.of(action),
        Set.of(properties.getAgentId()),
        Set.of(permission),
        false);
  }
}

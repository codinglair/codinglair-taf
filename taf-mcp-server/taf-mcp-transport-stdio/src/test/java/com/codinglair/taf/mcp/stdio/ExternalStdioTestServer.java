package com.codinglair.taf.mcp.stdio;

import com.codinglair.taf.core.Capability;
import com.codinglair.taf.mcp.jobs.JobRepository;
import com.codinglair.taf.mcp.jobs.JobService;
import com.codinglair.taf.mcp.jobs.LocalJobRepository;
import com.codinglair.taf.mcp.prompts.McpPromptReportService;
import com.codinglair.taf.mcp.prompts.PromptCatalog;
import com.codinglair.taf.mcp.prompts.ReportRepository;
import com.codinglair.taf.mcp.prompts.StoredResource;
import com.codinglair.taf.mcp.resources.EvidenceAccessScope;
import com.codinglair.taf.mcp.resources.McpContractDocumentation;
import com.codinglair.taf.mcp.resources.McpResourceService;
import com.codinglair.taf.mcp.resources.RuntimeCapabilityCatalog;
import com.codinglair.taf.mcp.security.ApprovalService;
import com.codinglair.taf.mcp.security.AuthorizationPolicyEngine;
import com.codinglair.taf.mcp.security.InMemoryAuditLog;
import com.codinglair.taf.mcp.security.McpEnforcementService;
import com.codinglair.taf.mcp.security.PolicyRule;
import com.codinglair.taf.mcp.security.ResponseRedactor;
import com.codinglair.taf.mcp.tools.McpWorkflowTools;
import com.codinglair.taf.mcp.tools.WorkflowOutcome;
import com.codinglair.taf.mcp.tools.WorkflowResult;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(proxyBeanMethods = false)
public class ExternalStdioTestServer {
  public static void main(String[] args) {
    StdioProtocolInput.install();
    SpringApplication.run(ExternalStdioTestServer.class, args);
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  ResponseRedactor responseRedactor() {
    return new ResponseRedactor(Set.of("stdio-secret-canary"));
  }

  @Bean
  AuthorizationPolicyEngine authorizationPolicyEngine() {
    return new AuthorizationPolicyEngine(
        List.of(
            new PolicyRule(
                "stdio-test-allow",
                PolicyRule.Effect.ALLOW,
                Set.of("*"),
                Set.of("*"),
                Set.of("*"),
                Set.of("*"),
                Set.of("*"),
                Set.of("*"),
                Set.of("*"),
                false)));
  }

  @Bean
  JobRepository jobRepository(TafMcpStdioProperties properties) {
    return new LocalJobRepository(properties.getWorkspaceRoot().resolve("jobs"));
  }

  @Bean
  JobService jobService(JobRepository repository, Clock clock) {
    return new JobService(repository, clock);
  }

  @Bean
  McpWorkflowTools workflowTools(
      TafMcpStdioProperties properties,
      AuthorizationPolicyEngine policy,
      JobService jobs,
      JobRepository repository,
      Clock clock,
      ResponseRedactor redactor) {
    var audit = new InMemoryAuditLog(clock, redactor);
    var enforcement =
        new McpEnforcementService(policy, new ApprovalService(clock), audit, redactor);
    return new McpWorkflowTools(
        properties.getWorkspaceRoot(),
        enforcement,
        jobs,
        repository,
        _ -> new WorkflowResult(WorkflowOutcome.SUCCEEDED, "validated", List.of()),
        (jobId, _) -> {
          while (repository.find(jobId).orElseThrow().state()
              != com.codinglair.taf.mcp.jobs.JobState.CANCEL_REQUESTED) {
            try {
              Thread.sleep(10);
            } catch (InterruptedException interrupted) {
              Thread.currentThread().interrupt();
              return new WorkflowResult(WorkflowOutcome.CANCELLED, "interrupted", List.of());
            }
          }
          return new WorkflowResult(WorkflowOutcome.CANCELLED, "cancelled", List.of());
        },
        Executors.newVirtualThreadPerTaskExecutor(),
        clock,
        redactor);
  }

  @Bean
  McpPromptReportService promptReportService(
      AuthorizationPolicyEngine policy, Clock clock, ResponseRedactor redactor) {
    var enforcement =
        new McpEnforcementService(
            policy, new ApprovalService(clock), new InMemoryAuditLog(clock, redactor), redactor);
    return new McpPromptReportService(PromptCatalog.standard(), reportRepository(), enforcement);
  }

  private ReportRepository reportRepository() {
    var access = new EvidenceAccessScope("local-user", "local", "local");
    return jobId ->
        List.of(
            new StoredResource(
                access,
                jobId,
                "report",
                "taf://report/" + jobId,
                "text/plain",
                "sanitized result stdio-secret-canary",
                true),
            new StoredResource(
                access,
                jobId,
                "trace",
                "taf://evidence/" + jobId + "/trace",
                "text/plain",
                "bounded trace",
                true));
  }

  @Bean
  McpResourceService resourceService(AuthorizationPolicyEngine policy) {
    var capabilities =
        new RuntimeCapabilityCatalog(
            List.of(
                new Capability("stdio", "STDIO transport", Capability.CapabilityType.CONTROLLER)),
            Map.of("stdio", "1.0.0"),
            "1.0.0");
    return new McpResourceService(
        capabilities,
        McpContractDocumentation.load(getClass().getClassLoader()),
        _ -> List.of(),
        policy,
        List.of(),
        List.of());
  }
}

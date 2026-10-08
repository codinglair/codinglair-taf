package com.codinglair.taf.mcp.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.core.Capability;
import com.codinglair.taf.mcp.jobs.JobRepository;
import com.codinglair.taf.mcp.jobs.JobService;
import com.codinglair.taf.mcp.jobs.LocalJobRepository;
import com.codinglair.taf.mcp.resources.McpContractDocumentation;
import com.codinglair.taf.mcp.resources.McpResourceService;
import com.codinglair.taf.mcp.resources.RuntimeCapabilityCatalog;
import com.codinglair.taf.mcp.security.ApprovalService;
import com.codinglair.taf.mcp.security.AuthorizationPolicyEngine;
import com.codinglair.taf.mcp.security.InMemoryAuditLog;
import com.codinglair.taf.mcp.security.McpEnforcementService;
import com.codinglair.taf.mcp.security.PolicyRule;
import com.codinglair.taf.mcp.security.ResponseRedactor;
import com.codinglair.taf.mcp.tools.ConfiguredCapability;
import com.codinglair.taf.mcp.tools.DefaultCapabilityPreflight;
import com.codinglair.taf.mcp.tools.McpWorkflowTools;
import com.codinglair.taf.mcp.tools.ToolResponse;
import com.codinglair.taf.mcp.tools.WorkflowOutcome;
import com.codinglair.taf.mcp.tools.WorkflowResult;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AppleHttpTransportContractTest {
  @TempDir Path workspace;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void discoversValidatesExecutesAndCancelsAuthorizedAppleFixture() {
    authenticate();
    var properties = new TafMcpHttpProperties();
    properties.setWorkspaceRoot(workspace);
    var caller = new HttpCallerContext(properties);
    var policy = allowAll();
    var catalog =
        new RuntimeCapabilityCatalog(
            List.of(
                new Capability(
                    "mobile.apple", "Apple Appium", Capability.CapabilityType.CONTROLLER)),
            Map.of("mobile.apple", "1.3.0"),
            "1.3.0");
    var resourceService =
        new McpResourceService(
            catalog,
            McpContractDocumentation.load(getClass().getClassLoader()),
            _ -> List.of(),
            policy,
            List.of(),
            List.of());

    String discovery = new HttpResources(resourceService, caller).capabilities();
    assertThat(discovery)
        .contains("mobile.apple", "execute-fixture", "does not allocate")
        .doesNotContain("apple-secret-canary", "https://");

    Clock clock = Clock.systemUTC();
    JobRepository repository = new LocalJobRepository(workspace.resolve("jobs"));
    var jobs = new JobService(repository, clock);
    var redactor = new ResponseRedactor(Set.of("apple-secret-canary"));
    var enforcement =
        new McpEnforcementService(
            policy, new ApprovalService(clock), new InMemoryAuditLog(clock, redactor), redactor);
    try (var workflows =
        new McpWorkflowTools(
            workspace,
            enforcement,
            jobs,
            repository,
            _ -> new WorkflowResult(WorkflowOutcome.SUCCEEDED, "validated", List.of()),
            (jobId, _) -> {
              while (repository.find(jobId).orElseThrow().state()
                  != com.codinglair.taf.mcp.jobs.JobState.CANCEL_REQUESTED) {
                Thread.onSpinWait();
              }
              return new WorkflowResult(WorkflowOutcome.CANCELLED, "cancelled", List.of());
            },
            Executors.newVirtualThreadPerTaskExecutor(),
            clock,
            redactor,
            new DefaultCapabilityPreflight(
                List.of(
                    new ConfiguredCapability(
                        "mobile.apple",
                        "fixture",
                        "kind",
                        true,
                        true,
                        "TEST_OWNED",
                        "DEDICATED_RESOURCE",
                        Set.of("validate", "execute-fixture")))))) {
      var tools = new HttpWorkflowTools(workflows, caller, properties);
      assertThat(tools.validate(request("validate", "validate-apple", null)).outcome())
          .isEqualTo(WorkflowOutcome.SUCCEEDED);
      ToolResponse execution = tools.execute(request("execute", "execute-apple", null));
      assertThat(execution.status()).isEqualTo(ToolResponse.Status.ACCEPTED);
      String jobId = execution.jobReference().substring("taf://job/".length());
      assertThat(tools.cancel(request("cancel", "cancel-apple", jobId)).summary())
          .contains("cancellation");
      awaitCancelled(repository, jobId);
    }
  }

  private static void awaitCancelled(JobRepository repository, String jobId) {
    long deadline = System.nanoTime() + java.time.Duration.ofSeconds(2).toNanos();
    while (System.nanoTime() < deadline) {
      var state = repository.find(new com.codinglair.taf.mcp.jobs.JobId(jobId)).orElseThrow().state();
      if (state == com.codinglair.taf.mcp.jobs.JobState.CANCELLED) return;
      Thread.onSpinWait();
    }
    throw new AssertionError("Apple fixture job did not reach CANCELLED");
  }

  private HttpWorkflowRequest request(String operation, String id, String targetJobId) {
    var arguments = new java.util.LinkedHashMap<String, Object>();
    arguments.put("workspace", workspace.toString());
    arguments.put("selector", "AppleFixture");
    arguments.put(
        "requiredCapabilities",
        List.of(
            Map.of(
                "capabilityId",
                "mobile.apple",
                "instance",
                "fixture",
                "operations",
                List.of(operation.equals("validate") ? "validate" : "execute-fixture"))));
    if (targetJobId != null) arguments.put("targetJobId", targetJobId);
    return new HttpWorkflowRequest(
        "1.0", id, operation, 10, Optional.of("key-" + id), Optional.empty(), arguments);
  }

  private static AuthorizationPolicyEngine allowAll() {
    return new AuthorizationPolicyEngine(
        List.of(
            new PolicyRule(
                "allow",
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

  private static void authenticate() {
    var jwt =
        new Jwt(
            "token",
            Instant.now().minusSeconds(1),
            Instant.now().plusSeconds(60),
            Map.of("alg", "none"),
            Map.of("sub", "http-user", "taf_project", "project", "taf_environment", "kind"));
    var authorities =
        List.of(
            new SimpleGrantedAuthority("SCOPE_taf.resources.read"),
            new SimpleGrantedAuthority("SCOPE_taf.tools.validate"),
            new SimpleGrantedAuthority("SCOPE_taf.tools.execute"),
            new SimpleGrantedAuthority("SCOPE_taf.tools.cancel"));
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, authorities));
  }
}

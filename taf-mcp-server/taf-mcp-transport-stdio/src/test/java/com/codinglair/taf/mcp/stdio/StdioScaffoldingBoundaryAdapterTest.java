package com.codinglair.taf.mcp.stdio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.mcp.security.ApprovalService;
import com.codinglair.taf.mcp.security.AuthorizationPolicyEngine;
import com.codinglair.taf.mcp.security.InMemoryAuditLog;
import com.codinglair.taf.mcp.security.McpEnforcementService;
import com.codinglair.taf.mcp.security.PolicyRule;
import com.codinglair.taf.mcp.security.ResponseRedactor;
import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine;
import com.codinglair.taf.mcp.tools.BlueprintScaffoldingService;
import com.codinglair.taf.mcp.tools.ScaffoldCompiler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StdioScaffoldingBoundaryAdapterTest {
  @TempDir Path root;

  @Test
  void sharedBoundaryRejectsSutRootBeforeTheAdapterCanTriggerMutation() throws Exception {
    Path sut = Files.createDirectory(root.resolve("sut"));
    Path protectedPom = sut.resolve("pom.xml");
    Files.writeString(protectedPom, "protected");
    var compiled = new AtomicBoolean();
    var arguments = blueprintArguments();
    arguments.put("automationRoot", sut.toString());
    arguments.put("sutRoot", sut.toString());

    assertThatThrownBy(
            () ->
                new StdioScaffoldingTools(service(compiled), new TafMcpStdioProperties())
                    .scaffold(
                        new StdioWorkflowRequest(
                            "1.0",
                            "request",
                            "scaffold",
                            "project",
                            "local",
                            30,
                            Optional.empty(),
                            Optional.empty(),
                            arguments)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be the SUT build root");
    assertThat(compiled).isFalse();
    assertThat(Files.readString(protectedPom)).isEqualTo("protected");
  }

  private BlueprintScaffoldingService service(AtomicBoolean compiled) {
    Clock clock = Clock.systemUTC();
    var approvals = new ApprovalService(clock);
    var redactor = new ResponseRedactor(Set.of());
    var policy =
        new AuthorizationPolicyEngine(
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
    return new BlueprintScaffoldingService(
        root,
        new BlueprintCompositionEngine(),
        new McpEnforcementService(
            policy, approvals, new InMemoryAuditLog(clock, redactor), redactor),
        approvals,
        _ -> {
          compiled.set(true);
          return new ScaffoldCompiler.CompilationResult(true, "compiled");
        });
  }

  private static java.util.HashMap<String, Object> blueprintArguments() {
    return new java.util.HashMap<>(
        Map.of(
            "groupId", "com.example",
            "artifactId", "automation",
            "basePackage", "com.example.automation",
            "tafVersion", "1.3.1",
            "capabilities", List.of("WEB"),
            "runner", "TESTNG",
            "reporting", "NONE",
            "testDefinitions", "FILE_CSV",
            "publish", true));
  }
}

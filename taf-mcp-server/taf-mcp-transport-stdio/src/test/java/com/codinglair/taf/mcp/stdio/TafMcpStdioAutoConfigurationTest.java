package com.codinglair.taf.mcp.stdio;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.mcp.McpProductionServiceAutoConfiguration;
import com.codinglair.taf.mcp.security.AuthorizationContext;
import com.codinglair.taf.mcp.security.AuthorizationPolicyEngine;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class TafMcpStdioAutoConfigurationTest {
  @TempDir Path workspace;

  @Test
  void explicitlyAuthorizedLocalScaffoldDoesNotRequireApproval() {
    try (var context = context(true, "state")) {
      var properties = context.getBean(TafMcpStdioProperties.class);
      var policy = context.getBean(AuthorizationPolicyEngine.class);
      var decision =
          policy.decide(
              new AuthorizationContext(
                  properties.identity(),
                  properties.getProject(),
                  properties.getEnvironment(),
                  "scaffold",
                  "taf:working-tree:write"));
      assertThat(decision.allowed()).isTrue();
      assertThat(decision.approvalRequired()).isFalse();
    }
  }

  @Test
  void localScaffoldAuthorizationRemainsOptIn() {
    try (var context = context(false, "state-default")) {
      assertThat(context.containsBean("tafMcpStdioScaffoldWritePolicy")).isFalse();
      assertThat(context.containsBean("tafMcpStdioDependencyWritePolicy")).isFalse();
    }
  }

  private AnnotationConfigApplicationContext context(boolean authorized, String stateDirectory) {
    var context = new AnnotationConfigApplicationContext();
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "spring.ai.mcp.server.stdio", "true",
                    "taf.mcp.stdio.scaffold-authorized", Boolean.toString(authorized),
                    "taf.mcp.stdio.workspace-root", workspace.toString(),
                    "taf.mcp.application.workspace-root", workspace.toString(),
                    "taf.mcp.application.state-root",
                        workspace.resolve(stateDirectory).toString())));
    context.register(
        McpProductionServiceAutoConfiguration.class, TafMcpStdioAutoConfiguration.class);
    context.refresh();
    return context;
  }
}

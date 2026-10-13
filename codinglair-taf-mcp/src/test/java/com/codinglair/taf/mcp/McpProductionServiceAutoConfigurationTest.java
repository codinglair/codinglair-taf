package com.codinglair.taf.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.mcp.jobs.JobService;
import com.codinglair.taf.mcp.prompts.McpPromptReportService;
import com.codinglair.taf.mcp.resources.McpResourceService;
import com.codinglair.taf.mcp.security.McpEnforcementService;
import com.codinglair.taf.mcp.tools.McpWorkflowTools;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class McpProductionServiceAutoConfigurationTest {
  @TempDir Path temporaryDirectory;

  @Test
  void providesSharedProductionServicesByDefault() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(McpProductionServiceAutoConfiguration.class))
        .withPropertyValues(
            "taf.mcp.application.workspace-root=" + temporaryDirectory,
            "taf.mcp.application.state-root=" + temporaryDirectory.resolve("state"))
        .run(
            context -> {
              assertThat(context).hasSingleBean(McpWorkflowTools.class);
              assertThat(context).hasSingleBean(McpResourceService.class);
              assertThat(context).hasSingleBean(McpPromptReportService.class);
              assertThat(context).hasSingleBean(McpEnforcementService.class);
              assertThat(context).hasSingleBean(JobService.class);
            });
  }

  @Test
  void backsOffWhenProductionCompositionIsDisabled() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(McpProductionServiceAutoConfiguration.class))
        .withPropertyValues("taf.mcp.application.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(McpWorkflowTools.class);
              assertThat(context).doesNotHaveBean(McpResourceService.class);
              assertThat(context).doesNotHaveBean(McpPromptReportService.class);
            });
  }

  @Test
  void leavesPromptCompositionToKindReferenceMode() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(McpProductionServiceAutoConfiguration.class))
        .withPropertyValues(
            "taf.mcp.kind-reference.enabled=true",
            "taf.mcp.application.state-root=" + temporaryDirectory.resolve("kind-state"))
        .run(context -> assertThat(context).doesNotHaveBean(McpPromptReportService.class));
  }
}

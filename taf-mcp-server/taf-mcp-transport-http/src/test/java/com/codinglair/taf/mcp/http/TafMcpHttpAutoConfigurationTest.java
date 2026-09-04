package com.codinglair.taf.mcp.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.codinglair.taf.mcp.prompts.McpPromptReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@DisplayName("TAF MCP HTTP auto-configuration")
class TafMcpHttpAutoConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(TafMcpHttpAutoConfiguration.class));

  @Nested
  @DisplayName("conditional composition")
  class ConditionalComposition {
    @Test
    @DisplayName("backs off completely when the HTTP transport is disabled")
    void disabled() {
      runner
          .withPropertyValues("taf.mcp.http.enabled=false")
          .run(
              context ->
                  assertThat(context)
                      .doesNotHaveBean(TafMcpHttpProperties.class)
                      .doesNotHaveBean(HttpAdmissionFilter.class));
    }

    @Test
    @DisplayName("creates bounded infrastructure without optional shared services")
    void infrastructureOnly() {
      runner
          .withBean(JwtDecoder.class, () -> token -> null)
          .run(
              context ->
                  assertThat(context)
                      .hasSingleBean(TafMcpHttpProperties.class)
                      .hasSingleBean(HttpAdmissionFilter.class)
                      .doesNotHaveBean(HttpWorkflowTools.class)
                      .doesNotHaveBean(HttpResources.class)
                      .doesNotHaveBean(HttpPromptReports.class));
    }

    @Test
    @DisplayName("binds prompts only when the governed shared service is available")
    void promptBindingIsConditional() {
      runner
          .withBean(JwtDecoder.class, () -> token -> null)
          .withBean(McpPromptReportService.class, () -> mock(McpPromptReportService.class))
          .run(context -> assertThat(context).hasSingleBean(HttpPromptReports.class));
    }
  }
}

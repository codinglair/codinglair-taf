package com.codinglair.taf.mcp.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.mcp.prompts.McpPromptReportService;
import com.codinglair.taf.mcp.resources.ResourceAccessDeniedException;
import com.codinglair.taf.mcp.resources.ResourceRequestContext;
import com.codinglair.taf.mcp.security.CallerIdentity;
import com.codinglair.taf.mcp.security.IdentityKind;
import com.codinglair.taf.mcp.security.Transport;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("Kind reference prompt composition")
class KindReferencePromptConfigurationTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(KindReferencePromptConfiguration.class);

  @Test
  @DisplayName("backs off unless the Kind reference deployment opts in")
  void backsOffByDefault() {
    contextRunner.run(context -> assertThat(context).doesNotHaveBean(McpPromptReportService.class));
  }

  @Test
  @DisplayName("binds standard prompts with a narrowly scoped default-deny policy")
  void bindsScopedPrompts() {
    contextRunner
        .withPropertyValues("taf.mcp.kind-reference.enabled=true")
        .run(
            context -> {
              var service = context.getBean(McpPromptReportService.class);
              var allowed = request("kind-smoke", "kind");
              assertThat(service.prompts(allowed, "correlation-1", Transport.STREAMABLE_HTTP))
                  .extracting(prompt -> prompt.name())
                  .contains("taf.qa.execution-summary");
              assertThatThrownBy(
                      () ->
                          service.prompts(
                              request("another-project", "kind"),
                              "correlation-2",
                              Transport.STREAMABLE_HTTP))
                  .isInstanceOf(ResourceAccessDeniedException.class);
            });
  }

  private static ResourceRequestContext request(String project, String environment) {
    var identity =
        new CallerIdentity(
            "kind-user", Set.of("SCOPE_taf.prompts.read"), "human:kind-user", IdentityKind.HUMAN);
    return new ResourceRequestContext(identity, project, environment);
  }
}

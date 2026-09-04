package com.codinglair.taf.mcp.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.mcp.stdio.StdioPromptReports;
import com.codinglair.taf.mcp.stdio.StdioWorkflowRequest;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;

@DisplayName("STDIO and Streamable HTTP schema compatibility")
class TransportSchemaEquivalenceTest {
  @Test
  @DisplayName("uses the same v1 request fields except server-owned authorization context")
  void usesEquivalentRequestSchema() {
    Set<String> stdio = components(StdioWorkflowRequest.class);
    Set<String> http = components(HttpWorkflowRequest.class);

    assertThat(stdio).containsAll(http).contains("projectId", "environment");
    assertThat(http).doesNotContain("projectId", "environment");
    assertThat(stdio)
        .containsExactlyInAnyOrderElementsOf(
            java.util.stream.Stream.concat(
                    http.stream(), java.util.stream.Stream.of("projectId", "environment"))
                .collect(Collectors.toSet()));
  }

  @Test
  @DisplayName("publishes schema-equivalent prompt report evidence and diagnostic operations")
  void usesEquivalentPromptAndReportSurface() {
    assertThat(annotatedNames(StdioPromptReports.class, McpPrompt.class))
        .containsExactlyInAnyOrderElementsOf(
            annotatedNames(HttpPromptReports.class, McpPrompt.class));
    assertThat(annotatedNames(StdioPromptReports.class, McpResource.class))
        .containsExactlyInAnyOrderElementsOf(
            annotatedNames(HttpPromptReports.class, McpResource.class));
    assertThat(annotatedNames(StdioPromptReports.class, McpTool.class))
        .containsExactlyInAnyOrderElementsOf(
            annotatedNames(HttpPromptReports.class, McpTool.class));
  }

  private static Set<String> components(Class<?> type) {
    return Arrays.stream(type.getRecordComponents())
        .map(RecordComponent::getName)
        .collect(Collectors.toSet());
  }

  private static Set<String> annotatedNames(
      Class<?> type, Class<? extends java.lang.annotation.Annotation> annotationType) {
    return Arrays.stream(type.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(annotationType))
        .map(Method::getName)
        .collect(Collectors.toSet());
  }
}

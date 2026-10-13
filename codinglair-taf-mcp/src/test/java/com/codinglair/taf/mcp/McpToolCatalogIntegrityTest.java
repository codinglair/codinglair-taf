package com.codinglair.taf.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;

@DisplayName("Production MCP tool catalog integrity")
class McpToolCatalogIntegrityTest {
  @Test
  @DisplayName("accepts a complete annotation-derived catalog")
  void acceptsCompleteCatalog() {
    assertThat(McpToolCatalogIntegrity.requireComplete(List.of(new CompleteTools())))
        .containsExactlyInAnyOrder("one", "two", "three", "four", "five", "six", "seven");
  }

  @Test
  @DisplayName("rejects an empty production tool composition")
  void rejectsEmptyCatalog() {
    assertThatThrownBy(() -> McpToolCatalogIntegrity.requireComplete(List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Required MCP tool registration is incomplete")
        .hasMessageContaining("found 0");
  }

  static final class CompleteTools {
    @McpTool(name = "one")
    public void one() {}

    @McpTool(name = "two")
    public void two() {}

    @McpTool(name = "three")
    public void three() {}

    @McpTool(name = "four")
    public void four() {}

    @McpTool(name = "five")
    public void five() {}

    @McpTool(name = "six")
    public void six() {}

    @McpTool(name = "seven")
    public void seven() {}
  }
}

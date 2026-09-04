package com.codinglair.taf.smoke;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.codinglair.taf.mcp.http.TafMcpHttpAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MCP HTTP staged consumer")
class McpHttpSmokeTest {
  @Test
  @DisplayName("resolves the HTTP transport without internal fixtures")
  void resolvesTheHttpTransportWithoutInternalFixtures() {
    assertNotNull(TafMcpHttpAutoConfiguration.class);
  }
}

package com.codinglair.taf.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.mcp.http.TafMcpHttpAutoConfiguration;
import org.testng.annotations.Test;
class McpHttpSmokeTest extends com.codinglair.taf.runtime.testng.TafBaseTest {
  @Test
  void resolvesTheHttpTransportWithoutInternalFixtures() {
    assertThat(TafMcpHttpAutoConfiguration.class).isNotNull();
  }
}

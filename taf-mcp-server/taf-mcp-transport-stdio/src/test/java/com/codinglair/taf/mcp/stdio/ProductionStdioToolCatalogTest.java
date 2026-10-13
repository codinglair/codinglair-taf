package com.codinglair.taf.mcp.stdio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Production STDIO MCP tool registration")
class ProductionStdioToolCatalogTest {
  @TempDir Path temporaryRoot;

  @Test
  @DisplayName("actual production application exposes the complete tool catalog")
  void exposesCompleteToolCatalog() {
    assertTimeoutPreemptively(
        Duration.ofSeconds(30),
        () -> {
          var transport = new StdioClientTransport(parameters(), McpJsonDefaults.getMapper());
          try (var client =
              McpClient.sync(transport)
                  .requestTimeout(Duration.ofSeconds(10))
                  .initializationTimeout(Duration.ofSeconds(10))
                  .build()) {
            client.initialize();
            assertThat(client.listTools().tools())
                .extracting(McpSchema.Tool::name)
                .containsExactlyInAnyOrder(
                    "validate", "compile", "build", "execute", "cancel", "scaffold", "diagnose");
          }
        });
  }

  private ServerParameters parameters() {
    return ServerParameters.builder(javaExecutable())
        .args(
            "-cp",
            System.getProperty("java.class.path"),
            TafMcpStdioApplication.class.getName(),
            "--taf.mcp.stdio.workspace-root=" + temporaryRoot,
            "--taf.mcp.application.workspace-root=" + temporaryRoot,
            "--taf.mcp.application.state-root=" + temporaryRoot.resolve("state"),
            "--spring.ai.mcp.client.enabled=false",
            "--spring.main.banner-mode=off")
        .build();
  }

  private static String javaExecutable() {
    String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
    return Path.of(System.getProperty("java.home"), "bin", executable).toAbsolutePath().toString();
  }
}

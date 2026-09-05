package com.codinglair.taf.mcp.stdio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("External Spring AI STDIO client compatibility")
class StdioExternalClientSmokeTest {
  private static final Pattern JOB_REFERENCE = Pattern.compile("taf://job/([A-Za-z0-9-]+)");

  @TempDir(cleanup = CleanupMode.NEVER)
  Path workspace;

  @AfterEach
  void releaseWorkspaceAfterServerShutdown() throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    Throwable lastFailure = null;
    do {
      try (var paths = Files.walk(workspace)) {
        paths.sorted(Comparator.reverseOrder()).forEach(StdioExternalClientSmokeTest::delete);
        return;
      } catch (WorkspaceDeletionException failure) {
        lastFailure = failure.getCause();
        LockSupport.parkNanos(Duration.ofMillis(25).toNanos());
      }
    } while (System.nanoTime() < deadline);
    throw new IllegalStateException("STDIO server did not release its test workspace", lastFailure);
  }

  @Test
  @DisplayName("connects discovers invokes retrieves and cancels on Windows-compatible process IO")
  void exercisesRepresentativeClientFlow() {
    assertTimeoutPreemptively(
        Duration.ofSeconds(30),
        () -> {
          var errors = new StringBuilder();
          var transport = new StdioClientTransport(parameters(), McpJsonDefaults.getMapper());
          transport.setStdErrorHandler(line -> errors.append(line).append('\n'));
          try (var client =
              McpClient.sync(transport)
                  .requestTimeout(Duration.ofSeconds(10))
                  .initializationTimeout(Duration.ofSeconds(10))
                  .build()) {
            try {
              client.initialize();
            } catch (RuntimeException failure) {
              throw new IllegalStateException("server stderr:\n" + errors, failure);
            }
            assertThat(client.listTools().tools())
                .extracting(McpSchema.Tool::name)
                .containsExactlyInAnyOrder(
                    "validate", "compile", "build", "execute", "cancel", "diagnose");
            assertThat(client.listPrompts().prompts())
                .extracting(McpSchema.Prompt::name)
                .containsExactlyInAnyOrder(
                    "taf.qa.failure-analysis",
                    "taf.qa.execution-summary",
                    "taf.qa.environment-triage");
            assertThat(client.listResources().resources())
                .extracting(McpSchema.Resource::uri)
                .contains("taf://capabilities");

            var validation =
                client.callTool(call("validate", request("validate", "validate-1", null)));
            assertThat(validation.isError())
                .as(validation.content().toString())
                .isNotEqualTo(Boolean.TRUE);
            assertThat(client.readResource(new McpSchema.ReadResourceRequest("taf://capabilities")))
                .extracting(result -> result.contents().toString())
                .asString()
                .contains("stdio");
            assertThat(
                    client.getPrompt(
                        new McpSchema.GetPromptRequest(
                            "taf.qa.execution-summary",
                            Map.of("reportReference", "taf://report/job-1"))))
                .extracting(result -> result.messages().toString())
                .asString()
                .contains("taf://report/job-1")
                .doesNotContain("stdio-secret-canary");
            assertThat(client.readResource(new McpSchema.ReadResourceRequest("taf://report/job-1")))
                .extracting(result -> result.contents().toString())
                .asString()
                .contains("sanitized result")
                .doesNotContain("stdio-secret-canary");
            var diagnostic =
                client.callTool(
                    call("diagnose", Map.of("jobId", "job-1", "kind", "EVIDENCE_INVENTORY")));
            assertThat(diagnostic.isError()).isNotEqualTo(Boolean.TRUE);
            assertThat(diagnostic.content().toString())
                .contains("taf://evidence/job-1/trace")
                .doesNotContain("stdio-secret-canary");

            var execution = client.callTool(call("execute", request("execute", "execute-1", null)));
            String executionText = execution.content().toString();
            var matcher = JOB_REFERENCE.matcher(executionText);
            assertThat(matcher.find()).as(executionText).isTrue();
            String jobId = matcher.group(1);
            var cancellation =
                client.callTool(call("cancel", request("cancel", "cancel-1", jobId)));
            assertThat(cancellation.isError()).isNotEqualTo(Boolean.TRUE);
            assertThat(cancellation.content().toString()).contains("cancellation");
          }
          assertThat(errors).doesNotContain("stdio-secret-canary", "Application run failed");
        });
  }

  @Test
  @DisplayName("rejects a malformed frame and continues serving protocol messages")
  void survivesMalformedFrame() {
    assertTimeoutPreemptively(
        Duration.ofSeconds(20),
        () -> {
          Process process =
              new ProcessBuilder(serverCommand())
                  .redirectError(ProcessBuilder.Redirect.INHERIT)
                  .start();
          try (var input =
                  new java.io.BufferedReader(
                      new java.io.InputStreamReader(
                          process.getInputStream(), StandardCharsets.UTF_8));
              var output =
                  new java.io.BufferedWriter(
                      new java.io.OutputStreamWriter(
                          process.getOutputStream(), StandardCharsets.UTF_8))) {
            output.write("not-json\n");
            output.write(
                """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"raw-smoke","version":"1.0"}}}
                """);
            output.flush();
            String response =
                java.util.concurrent.CompletableFuture.supplyAsync(
                        () -> {
                          try {
                            return input.readLine();
                          } catch (java.io.IOException failure) {
                            throw new java.io.UncheckedIOException(failure);
                          }
                        })
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(response).startsWith("{").contains("\"id\":1", "\"result\"");
          } finally {
            process.getOutputStream().close();
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
              process.destroy();
              if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                assertThat(process.waitFor(5, TimeUnit.SECONDS))
                    .as("STDIO test server terminates after forced shutdown")
                    .isTrue();
              }
            }
          }
        });
  }

  private ServerParameters parameters() {
    var command = serverCommand();
    return ServerParameters.builder(command.getFirst())
        .args(command.subList(1, command.size()))
        .build();
  }

  private java.util.List<String> serverCommand() {
    return java.util.List.of(
        javaExecutable(),
        "-cp",
        System.getProperty("java.class.path"),
        ExternalStdioTestServer.class.getName(),
        "--taf.mcp.stdio.workspace-root=" + workspace,
        "--spring.ai.mcp.client.enabled=false",
        "--spring.main.banner-mode=off");
  }

  private Map<String, Object> request(String operation, String requestId, String targetJobId) {
    var request = new java.util.LinkedHashMap<String, Object>();
    request.put("schemaVersion", "1.0");
    request.put("requestId", requestId);
    request.put("operation", operation);
    request.put("projectId", "local");
    request.put("environment", "local");
    request.put("timeoutSeconds", 10);
    request.put("idempotencyKey", "key-" + requestId);
    request.put("approvalReference", "");
    var arguments = new java.util.LinkedHashMap<String, Object>();
    arguments.put("workspace", "");
    arguments.put("selector", "SmokeSuite");
    if (targetJobId != null) {
      arguments.put("targetJobId", targetJobId);
    }
    request.put("arguments", arguments);
    return Map.of("request", request);
  }

  private static McpSchema.CallToolRequest call(String name, Map<String, Object> arguments) {
    return new McpSchema.CallToolRequest(name, arguments);
  }

  private static String javaExecutable() {
    String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
    Path path = Path.of(System.getProperty("java.home"), "bin", executable).toAbsolutePath();
    if (!java.nio.file.Files.isRegularFile(path)) {
      throw new IllegalStateException("Java executable is unavailable under java.home: " + path);
    }
    return path.toString();
  }

  private static void delete(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (java.io.IOException failure) {
      throw new WorkspaceDeletionException(failure);
    }
  }

  private static final class WorkspaceDeletionException extends RuntimeException {
    private WorkspaceDeletionException(java.io.IOException cause) {
      super(cause);
    }
  }
}

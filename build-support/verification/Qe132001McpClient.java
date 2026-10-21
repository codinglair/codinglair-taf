import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** JDK-only packaged MCP client used by QE-132-001. */
public final class Qe132001McpClient {
  private Qe132001McpClient() {}

  public static void main(String[] arguments) throws Exception {
    if (arguments.length != 5) {
      throw new IllegalArgumentException(
          "usage: <server-classpath-file> <workspace> <automationRoot> <sutRoot> <publish>");
    }
    Path classpathFile = Path.of(arguments[0]);
    Path workspace = Path.of(arguments[1]).toAbsolutePath();
    String automationRoot = Path.of(arguments[2]).toAbsolutePath().toString();
    String sutRoot = Path.of(arguments[3]).toAbsolutePath().toString();
    boolean publish = Boolean.parseBoolean(arguments[4]);
    Path stderr = workspace.resolve("mcp-stderr.log");
    Files.createDirectories(workspace);
    var command =
        List.of(
            javaExecutable(),
            "-cp",
            Files.readString(classpathFile).trim(),
            "com.codinglair.taf.mcp.stdio.TafMcpStdioApplication",
            "--taf.mcp.stdio.workspace-root=" + workspace,
            "--taf.mcp.stdio.project=qe-local",
            "--taf.mcp.stdio.scaffold-authorized=true",
            "--spring.ai.mcp.client.enabled=false",
            "--spring.main.banner-mode=off");
    Process process =
        new ProcessBuilder(command).redirectError(stderr.toFile()).start();
    try (var input =
            new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        var output =
            new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
      request(
          output,
          input,
          1,
          "initialize",
          "\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},"
              + "\"clientInfo\":{\"name\":\"qe-132-001\",\"version\":\"1\"}");
      notify(output, "notifications/initialized", "");
      String tools = request(output, input, 2, "tools/list", "");
      String call =
          request(
              output,
              input,
              3,
              "tools/call",
              "\"name\":\"scaffold\",\"arguments\":{\"request\":{"
                  + "\"schemaVersion\":\"1.0\",\"requestId\":\"qe-scaffold\","
                  + "\"operation\":\"scaffold\",\"projectId\":\"qe-local\","
                  + "\"environment\":\"local\",\"timeoutSeconds\":120,"
                  + "\"idempotencyKey\":\"qe-scaffold-key\",\"approvalReference\":\"\","
                  + "\"arguments\":{\"automationRoot\":\""
                  + json(automationRoot)
                  + "\",\"sutRoot\":\""
                  + json(sutRoot)
                  + "\",\"publish\":"
                  + publish
                  + ",\"groupId\":\"qe.acceptance\",\"artifactId\":\"automation\","
                  + "\"basePackage\":\"qe.acceptance\",\"tafVersion\":\"1.3.1\","
                  + "\"capabilities\":[\"API\"],\"runner\":\"testng\","
                  + "\"reporting\":\"allure\",\"testDefinitions\":\"FILE_CSV\"}}}");
      System.out.println("TOOLS=" + tools);
      System.out.println("SCAFFOLD=" + call);
    } finally {
      process.getOutputStream().close();
      if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly();
    }
  }

  private static String request(
      BufferedWriter output, BufferedReader input, int id, String method, String parameters)
      throws Exception {
    String separator = parameters.isEmpty() ? "" : ",";
    output.write(
        "{\"jsonrpc\":\"2.0\",\"id\":"
            + id
            + ",\"method\":\""
            + method
            + "\",\"params\":{"
            + parameters
            + "}}\n");
    output.flush();
    return CompletableFuture.supplyAsync(() -> read(input)).get(6, TimeUnit.MINUTES);
  }

  private static void notify(BufferedWriter output, String method, String parameters)
      throws IOException {
    output.write(
        "{\"jsonrpc\":\"2.0\",\"method\":\""
            + method
            + "\",\"params\":{"
            + parameters
            + "}}\n");
    output.flush();
  }

  private static String read(BufferedReader input) {
    try {
      String line = input.readLine();
      if (line == null) throw new IllegalStateException("packaged MCP server closed stdout");
      return line;
    } catch (IOException failure) {
      throw new IllegalStateException("cannot read packaged MCP response", failure);
    }
  }

  private static String json(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static String javaExecutable() {
    String name = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
    return Path.of(System.getProperty("java.home"), "bin", name).toString();
  }
}

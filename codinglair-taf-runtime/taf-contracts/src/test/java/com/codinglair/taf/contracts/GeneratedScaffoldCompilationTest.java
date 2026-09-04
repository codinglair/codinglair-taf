package com.codinglair.taf.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Generated contract scaffolds")
class GeneratedScaffoldCompilationTest {
  private final Path generatedDirectory = Path.of("target", "generated-contract-test");

  @Test
  @DisplayName("OpenAPI and AsyncAPI scaffolds compile against Runtime controllers")
  void compileAgainstRuntime() throws Exception {
    var service =
        new ContractService(List.of(new OpenApiContractAdapter(), new AsyncApiContractAdapter()));
    var openApi =
        scaffold(service, ContractFormat.OPENAPI, "/contracts/valid-openapi.yaml", "OrdersApi");
    var asyncApi =
        scaffold(
            service, ContractFormat.ASYNCAPI, "/contracts/valid-asyncapi.yaml", "OrdersEvents");

    var sources = List.of(write(openApi), write(asyncApi));
    var compiler = ToolProvider.getSystemJavaCompiler();
    assertThat(compiler).as("Java 25 compiler").isNotNull();
    var fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
    var units = fileManager.getJavaFileObjectsFromPaths(sources);
    var output = generatedDirectory.resolve("classes");
    Files.createDirectories(output);
    var repository = Path.of("..").toAbsolutePath().normalize();
    var runtimeClasspath =
        List.of(
                repository.resolve("taf-api-rest/target/classes"),
                repository.resolve("taf-messaging-core/target/classes"),
                repository.resolve("codinglair-taf-runtime-core/target/classes"))
            .stream()
            .map(Path::toString)
            .collect(Collectors.joining(System.getProperty("path.separator")));

    var compiled =
        compiler
            .getTask(
                null,
                fileManager,
                null,
                List.of("--release", "25", "-classpath", runtimeClasspath, "-d", output.toString()),
                null,
                units)
            .call();

    fileManager.close();
    assertThat(compiled).isTrue();
    assertThat(Files.exists(output.resolve("example/contracts/OrdersApi.class"))).isTrue();
    assertThat(Files.exists(output.resolve("example/contracts/OrdersEvents.class"))).isTrue();
  }

  private GeneratedContractAsset scaffold(
      ContractService service, ContractFormat format, String resource, String className)
      throws Exception {
    try (var stream = getClass().getResourceAsStream(resource)) {
      assertThat(stream).isNotNull();
      var document =
          new ContractDocument(
              format, "1", "v1", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
      var result =
          service.scaffold(new ContractScaffoldRequest(document, "example.contracts", className));
      assertThat(result.status()).isEqualTo(ContractValidationResult.Status.VALID);
      return result.assets().getFirst();
    }
  }

  private Path write(GeneratedContractAsset asset) throws Exception {
    var file = generatedDirectory.resolve(asset.relativePath());
    Files.createDirectories(file.getParent());
    Files.writeString(file, asset.content(), StandardCharsets.UTF_8);
    return file;
  }
}

package com.codinglair.taf.api.soap;

import static org.assertj.core.api.Assertions.assertThat;

import codinglair.taf.soap.calculator.CalculatorService;
import com.codinglair.taf.runtime.secret.ResolvedSecret;
import com.codinglair.taf.runtime.secret.SecretManager;
import com.codinglair.taf.runtime.secret.SecretRequestContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.tools.common.ToolContext;
import org.apache.cxf.tools.wsdlto.WSDLToJava;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Generated SOAP client lifecycle")
class GeneratedClientLifecycleTest {
  @TempDir Path temporaryDirectory;

  @Test
  @DisplayName("generates a compiled Jakarta client from the checked-in WSDL")
  void generatedClientCompiles() {
    assertThat(CalculatorService.class.getName())
        .isEqualTo("codinglair.taf.soap.calculator.CalculatorService");
  }

  @Test
  @DisplayName("configures generated clients for secret-backed X.509 signing")
  void configuresSignature() {
    var port = new CalculatorService().getCalculatorSoap11Port();
    SecretManager manager =
        new SecretManager() {
          public ResolvedSecret resolve(String reference, SecretRequestContext context) {
            return ResolvedSecret.of("private-signing-canary".toCharArray());
          }

          public void verifyReady(String reference) {}
        };
    var callback =
        new SecretReferenceCallbackHandler(
            manager,
            Map.of("signing-key", "secret://env/SIGNING_PASSWORD"),
            new SecretRequestContext("soap-signing", "session", "test", true));
    CxfWsSecurity.configureSignature(
        port, "signing-key", "ws-security/signing-crypto.properties", callback);
    assertThat(ClientProxy.getClient(port).getOutInterceptors())
        .anyMatch(WSS4JOutInterceptor.class::isInstance);
    assertThat(callback.toString()).doesNotContain("private-signing-canary", "SIGNING_PASSWORD");
  }

  @Test
  @DisplayName("two clean WSDL generation executions produce identical source manifests")
  void generationIsReproducible() throws Exception {
    Path first = temporaryDirectory.resolve("first");
    Path second = temporaryDirectory.resolve("second");
    generate(first);
    generate(second);
    assertThat(manifest(first)).isEqualTo(manifest(second));
  }

  private static void generate(Path destination) throws Exception {
    Files.createDirectories(destination);
    String[] arguments = {
      "-d",
      destination.toString(),
      "-validate",
      "-suppress-generated-date",
      Path.of("src", "test", "resources", "wsdl", "calculator.wsdl").toString()
    };
    new WSDLToJava(arguments).run(new ToolContext());
  }

  private static Map<String, String> manifest(Path root) throws Exception {
    Map<String, String> result = new TreeMap<>();
    try (var paths = Files.walk(root)) {
      for (Path path : paths.filter(Files::isRegularFile).toList()) {
        result.put(root.relativize(path).toString().replace('\\', '/'), sha256(path));
      }
    }
    assertThat(result).isNotEmpty();
    return result;
  }

  private static String sha256(Path path) throws Exception {
    return HexFormat.of()
        .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
  }
}

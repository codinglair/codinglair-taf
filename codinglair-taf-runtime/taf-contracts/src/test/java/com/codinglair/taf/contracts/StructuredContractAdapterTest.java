package com.codinglair.taf.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Structured contract adapters")
class StructuredContractAdapterTest {
  @Nested
  @DisplayName("Validation")
  class Validation {
    @ParameterizedTest(name = "{0} is valid={2}")
    @CsvSource({
      "valid-openapi.yaml,OPENAPI,true",
      "invalid-openapi.yaml,OPENAPI,false",
      "valid-asyncapi.yaml,ASYNCAPI,true",
      "invalid-asyncapi.yaml,ASYNCAPI,false"
    })
    @DisplayName("Valid and invalid fixtures produce structured results")
    void fixtures(String fixture, ContractFormat format, boolean valid) throws IOException {
      var service = service();

      var result = service.validate(document(format, fixture));

      assertThat(result.valid()).isEqualTo(valid);
      assertThat(result.provider()).isNotBlank();
      if (!valid) {
        assertThat(result.diagnostics())
            .allSatisfy(
                diagnostic -> {
                  assertThat(diagnostic.code()).isNotBlank();
                  assertThat(diagnostic.location()).isNotBlank();
                  assertThat(diagnostic.correctiveAction()).isNotBlank();
                });
      }
    }

    @Test
    @DisplayName("Malformed content is reported without throwing")
    void malformed() {
      var malformed = new ContractDocument(ContractFormat.OPENAPI, "3.1", "v1", "{not-json: [");

      assertThat(service().validate(malformed).status())
          .isEqualTo(ContractValidationResult.Status.INVALID);
    }

    @Test
    @DisplayName("Concurrent validation shares no mutable document state")
    void concurrent() throws Exception {
      var service = service();
      var document = document(ContractFormat.OPENAPI, "valid-openapi.yaml");
      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        var futures =
            IntStream.range(0, 100)
                .mapToObj(_ -> executor.submit(() -> service.validate(document)))
                .toList();
        for (var future : futures) assertThat(future.get().valid()).isTrue();
      }
    }
  }

  @Nested
  @DisplayName("Provider boundaries")
  class ProviderBoundaries {
    @Test
    @DisplayName("A provider failure is isolated and does not expose its message")
    void failureIsolation() {
      ContractAdapter failing =
          new ContractAdapter() {
            public String provider() {
              return "failing";
            }

            public ContractFormat format() {
              return ContractFormat.OPENAPI;
            }

            public ContractValidationResult validate(ContractDocument document) {
              throw new IllegalStateException("credential=do-not-leak");
            }

            public ContractScaffoldResult scaffold(ContractScaffoldRequest request) {
              throw new IllegalStateException("credential=do-not-leak");
            }
          };
      var service = new ContractService(List.of(failing, new AsyncApiContractAdapter()));

      var result = service.validate(document(ContractFormat.OPENAPI, "valid-openapi.yaml"));

      assertThat(result.status()).isEqualTo(ContractValidationResult.Status.PROVIDER_FAILURE);
      assertThat(result.diagnostics().getFirst().message()).doesNotContain("do-not-leak");
      assertThat(service.validate(document(ContractFormat.ASYNCAPI, "valid-asyncapi.yaml")).valid())
          .isTrue();
    }

    @Test
    @DisplayName("Missing and consumer providers return capability gaps")
    void capabilityGaps() {
      var missing =
          new ContractService(List.of())
              .validate(new ContractDocument(ContractFormat.CONSUMER, "1", "v1", "{}"));
      var explicit =
          new ContractService(List.of(new UnsupportedConsumerContractAdapter()))
              .validate(new ContractDocument(ContractFormat.CONSUMER, "1", "v1", "{}"));

      assertThat(missing.status()).isEqualTo(ContractValidationResult.Status.UNSUPPORTED);
      assertThat(explicit.status()).isEqualTo(ContractValidationResult.Status.UNSUPPORTED);
    }

    @Test
    @DisplayName("Duplicate providers are rejected deterministically")
    void duplicateProviders() {
      assertThatThrownBy(
              () ->
                  new ContractService(
                      List.of(new OpenApiContractAdapter(), new OpenApiContractAdapter())))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Duplicate adapter");
    }
  }

  private static ContractService service() {
    return new ContractService(
        List.of(new OpenApiContractAdapter(), new AsyncApiContractAdapter()));
  }

  private static ContractDocument document(ContractFormat format, String fixture) {
    try (var stream =
        StructuredContractAdapterTest.class.getResourceAsStream("/contracts/" + fixture)) {
      if (stream == null) throw new IllegalArgumentException("Missing fixture " + fixture);
      return new ContractDocument(
          format, "1", "2026-08-16", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException failure) {
      throw new IllegalStateException(failure);
    }
  }
}

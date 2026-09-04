package com.codinglair.taf.contracts;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.api.rest.RestRequest;
import com.codinglair.taf.api.rest.RestResponse;
import io.restassured.builder.ResponseBuilder;
import io.restassured.http.Method;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OpenAPI REST contract validator")
class OpenApiRestContractValidatorTest {
  @Test
  @DisplayName("Declared operation and response pass")
  void declaredExchange() {
    var validator = validator();

    assertThatCode(
            () ->
                validator.validate(
                    RestRequest.request(Method.GET, "/orders").build(), response(200)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Undeclared path, operation, and response fail with contract violations")
  void undeclaredExchange() {
    var validator = validator();

    assertThatThrownBy(
            () ->
                validator.validate(
                    RestRequest.request(Method.GET, "/missing").build(), response(200)))
        .isInstanceOf(ContractViolationException.class)
        .hasMessageContaining("path");
    assertThatThrownBy(
            () ->
                validator.validate(
                    RestRequest.request(Method.POST, "/orders").build(), response(200)))
        .isInstanceOf(ContractViolationException.class)
        .hasMessageContaining("operation");
    assertThatThrownBy(
            () ->
                validator.validate(
                    RestRequest.request(Method.GET, "/orders").build(), response(500)))
        .isInstanceOf(ContractViolationException.class)
        .hasMessageContaining("response");
  }

  private static OpenApiRestContractValidator validator() {
    try (var stream =
        OpenApiRestContractValidatorTest.class.getResourceAsStream(
            "/contracts/valid-openapi.yaml")) {
      if (stream == null) throw new IllegalStateException("Fixture missing");
      return new OpenApiRestContractValidator(
          new ContractDocument(
              ContractFormat.OPENAPI,
              "3.1",
              "v1",
              new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
    } catch (IOException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static RestResponse response(int status) {
    try {
      var constructor =
          RestResponse.class.getDeclaredConstructor(io.restassured.response.Response.class);
      constructor.setAccessible(true);
      return constructor.newInstance(
          new ResponseBuilder().setStatusCode(status).setBody("{}").build());
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException(failure);
    }
  }
}

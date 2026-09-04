package com.codinglair.taf.api.rest;

import static org.hamcrest.Matchers.equalTo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import io.restassured.response.Response;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Response and rich assertion facade retaining a native REST Assured escape hatch. */
public final class RestResponse {
  private final Response nativeResponse;

  RestResponse(Response nativeResponse) {
    this.nativeResponse = Objects.requireNonNull(nativeResponse);
  }

  public int statusCode() {
    return nativeResponse.statusCode();
  }

  public byte[] body() {
    return nativeResponse.asByteArray();
  }

  public String bodyAsString() {
    return new String(body(), StandardCharsets.UTF_8);
  }

  public String header(String name) {
    return nativeResponse.header(name);
  }

  public String cookie(String name) {
    return nativeResponse.cookie(name);
  }

  public Response nativeResponse() {
    return nativeResponse;
  }

  public RestResponse assertStatus(int expected) {
    nativeResponse.then().statusCode(expected);
    return this;
  }

  public RestResponse assertHeader(String name, String expected) {
    nativeResponse.then().header(name, expected);
    return this;
  }

  public RestResponse assertCookie(String name, String expected) {
    nativeResponse.then().cookie(name, expected);
    return this;
  }

  public RestResponse assertJsonPath(String path, Object expected) {
    nativeResponse.then().body(path, equalTo(expected));
    return this;
  }

  public RestResponse assertXmlPath(String path, Object expected) {
    nativeResponse.then().body(path, equalTo(expected));
    return this;
  }

  public RestResponse assertBody(String expected) {
    nativeResponse.then().body(equalTo(expected));
    return this;
  }

  public RestResponse assertJsonSchema(String schema) {
    try {
      var mapper = new ObjectMapper();
      var validator =
          JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
              .getSchema(mapper.readTree(schema));
      var errors = validator.validate(mapper.readTree(body()));
      if (!errors.isEmpty()) throw new AssertionError("JSON schema validation failed: " + errors);
      return this;
    } catch (java.io.IOException failure) {
      throw new IllegalArgumentException("Schema or response is not valid JSON", failure);
    }
  }
}

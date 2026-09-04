package com.codinglair.taf.api.rest;

import io.restassured.http.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reusable immutable REST request specification. */
public final class RestRequest {
  private final Method method;
  private final String path;
  private final Map<String, Object> headers;
  private final Map<String, Object> cookies;
  private final Map<String, Object> pathParameters;
  private final Map<String, Object> queryParameters;
  private final Map<String, Object> formParameters;
  private final byte[] body;
  private final String contentType;
  private final List<RestMultipart> multiparts;
  private final RestAuthentication authentication;

  private RestRequest(Builder builder) {
    method = Objects.requireNonNull(builder.method, "method");
    if (builder.path == null || builder.path.isBlank())
      throw new IllegalArgumentException("Request path must not be blank");
    path = builder.path;
    headers = Map.copyOf(builder.headers);
    cookies = Map.copyOf(builder.cookies);
    pathParameters = Map.copyOf(builder.pathParameters);
    queryParameters = Map.copyOf(builder.queryParameters);
    formParameters = Map.copyOf(builder.formParameters);
    body = builder.body.clone();
    contentType = builder.contentType;
    multiparts = List.copyOf(builder.multiparts);
    authentication = builder.authentication;
  }

  public static Builder request(Method method, String path) {
    return new Builder(method, path);
  }

  public Method method() {
    return method;
  }

  public String path() {
    return path;
  }

  public Map<String, Object> headers() {
    return headers;
  }

  public Map<String, Object> cookies() {
    return cookies;
  }

  public Map<String, Object> pathParameters() {
    return pathParameters;
  }

  public Map<String, Object> queryParameters() {
    return queryParameters;
  }

  public Map<String, Object> formParameters() {
    return formParameters;
  }

  public byte[] body() {
    return body.clone();
  }

  public String contentType() {
    return contentType;
  }

  public List<RestMultipart> multiparts() {
    return multiparts;
  }

  public RestAuthentication authentication() {
    return authentication;
  }

  public static final class Builder {
    private final Method method;
    private final String path;
    private final Map<String, Object> headers = new LinkedHashMap<>();
    private final Map<String, Object> cookies = new LinkedHashMap<>();
    private final Map<String, Object> pathParameters = new LinkedHashMap<>();
    private final Map<String, Object> queryParameters = new LinkedHashMap<>();
    private final Map<String, Object> formParameters = new LinkedHashMap<>();
    private byte[] body = new byte[0];
    private String contentType;
    private final List<RestMultipart> multiparts = new ArrayList<>();
    private RestAuthentication authentication = RestAuthentication.NONE;

    private Builder(Method method, String path) {
      this.method = method;
      this.path = path;
    }

    public Builder header(String name, Object value) {
      headers.put(requireName(name), Objects.requireNonNull(value));
      return this;
    }

    public Builder cookie(String name, Object value) {
      cookies.put(requireName(name), Objects.requireNonNull(value));
      return this;
    }

    public Builder pathParameter(String name, Object value) {
      pathParameters.put(requireName(name), Objects.requireNonNull(value));
      return this;
    }

    public Builder queryParameter(String name, Object value) {
      queryParameters.put(requireName(name), Objects.requireNonNull(value));
      return this;
    }

    public Builder formParameter(String name, Object value) {
      formParameters.put(requireName(name), Objects.requireNonNull(value));
      return this;
    }

    public Builder body(String value, String type) {
      return body(value.getBytes(StandardCharsets.UTF_8), type);
    }

    public Builder body(byte[] value, String type) {
      body = Objects.requireNonNull(value).clone();
      contentType = type;
      return this;
    }

    public Builder multipart(RestMultipart value) {
      multiparts.add(Objects.requireNonNull(value));
      return this;
    }

    public Builder authentication(RestAuthentication value) {
      authentication = Objects.requireNonNull(value);
      return this;
    }

    public RestRequest build() {
      return new RestRequest(this);
    }

    private static String requireName(String value) {
      if (value == null || value.isBlank())
        throw new IllegalArgumentException("Name must not be blank");
      return value;
    }
  }
}

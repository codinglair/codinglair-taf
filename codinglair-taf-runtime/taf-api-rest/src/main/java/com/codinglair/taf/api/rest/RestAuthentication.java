package com.codinglair.taf.api.rest;

import io.restassured.specification.RequestSpecification;

/** Pluggable request authentication. Implementations must not expose resolved credentials. */
@FunctionalInterface
public interface RestAuthentication {
  RestAuthentication NONE = specification -> {};

  void apply(RequestSpecification specification);
}

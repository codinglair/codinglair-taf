package com.codinglair.taf.api.rest;

/** OpenAPI integration boundary; implementations belong to the contract capability. */
@FunctionalInterface
public interface RestContractValidator {
  RestContractValidator NONE = (request, response) -> {};

  void validate(RestRequest request, RestResponse response);
}

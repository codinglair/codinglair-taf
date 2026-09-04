package com.codinglair.taf.api.rest;

import com.codinglair.taf.runtime.core.controller.TestController;
import io.restassured.specification.RequestSpecification;
import java.time.Duration;
import java.util.function.Predicate;

/** Public REST capability. */
public interface RestController extends TestController {
  RestResponse execute(RestRequest request);

  RestResponse await(
      RestRequest request, Predicate<RestResponse> condition, Duration timeout, Duration interval);

  RequestSpecification nativeSpecification();
}

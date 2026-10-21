package com.example.generated;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.api.rest.RestAutoConfiguration;
import com.codinglair.taf.api.rest.RestController;
import com.codinglair.taf.api.rest.RestRequest;
import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import io.restassured.http.Method;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.testng.annotations.Test;

/** Verifies a separately running SUT exclusively through the public REST controller contract. */
@ContextConfiguration(classes = {TafRuntimeAutoConfiguration.class, RestAutoConfiguration.class})
@TestPropertySource(
    properties = {
      "taf.api.rest.enabled=true",
      "taf.api.rest.controllers.sut.base-url=${sut.base-url}"
    })
public final class StandaloneSutBoundaryTest
    extends com.codinglair.taf.runtime.testng.TafBaseTest {
  @Test
  public void interactsWithStandaloneSutWithoutSharingItsApplicationContext() {
    String expected = System.getProperty("sut.expected-response");
    assertThat(expected).as("sut.expected-response").isNotBlank();

    controller(RestController.class, "sut")
        .execute(RestRequest.request(Method.GET, "/qualification").build())
        .assertStatus(200)
        .assertBody(expected);
  }
}

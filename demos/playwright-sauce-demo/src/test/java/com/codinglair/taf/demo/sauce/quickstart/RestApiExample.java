package com.codinglair.taf.demo.sauce.quickstart;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.codinglair.taf.api.rest.RestController;
import com.codinglair.taf.api.rest.RestRequest;
import com.codinglair.taf.api.rest.RestResponse;
import com.codinglair.taf.core.annotation.reporting.TestCaseId;
import com.codinglair.taf.demo.sauce.SauceDemoApplication;
import com.codinglair.taf.runtime.testng.TafBaseTest;
import io.restassured.http.Method;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

/** Copy-ready REST example. Run explicitly against the consumer project's authorized test API. */
@SpringBootTest(classes = SauceDemoApplication.class)
@ContextConfiguration(classes = SauceDemoApplication.class, inheritLocations = false)
public class RestApiExample extends TafBaseTest {
  @Test
  @TestCaseId("QS-REST-001")
  public void createsReadsAndRejectsAnOrder() {
    RestController orders = controller(RestController.class, "orders-api");

    RestResponse created =
        orders
            .execute(
                RestRequest.request(Method.POST, "/orders")
                    .header("X-Correlation-Id", testSession().getCorrelationContext().getTraceId())
                    .body("{\"sku\":\"BACKPACK\",\"quantity\":1}", "application/json")
                    .build())
            .assertStatus(201)
            .assertHeader("Content-Type", "application/json");
    String orderId = created.nativeResponse().jsonPath().getString("id");
    assertNotNull(orderId, "The create response must contain an order id");

    orders
        .execute(
            RestRequest.request(Method.GET, "/orders/{id}")
                .pathParameter("id", orderId)
                .queryParameter("include", "status")
                .build())
        .assertStatus(200)
        .assertJsonPath("id", orderId)
        .assertJsonPath("quantity", 1);

    RestResponse rejected =
        orders.execute(
            RestRequest.request(Method.POST, "/orders")
                .body("{\"quantity\":0}", "application/json")
                .build());
    rejected.assertStatus(400).assertJsonPath("code", "INVALID_ORDER");
    assertEquals(rejected.statusCode(), 400);
  }
}

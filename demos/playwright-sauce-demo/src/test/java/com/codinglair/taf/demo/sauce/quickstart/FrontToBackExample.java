package com.codinglair.taf.demo.sauce.quickstart;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import com.codinglair.taf.api.rest.RestController;
import com.codinglair.taf.api.rest.RestRequest;
import com.codinglair.taf.core.annotation.reporting.TestCaseId;
import com.codinglair.taf.database.DatabaseController;
import com.codinglair.taf.demo.sauce.SauceDemoApplication;
import com.codinglair.taf.demo.sauce.page.OrdersPage;
import com.codinglair.taf.runtime.core.condition.AwaitableAssertion;
import com.codinglair.taf.runtime.testng.TafBaseTest;
import com.codinglair.taf.web.playwright.PlaywrightObjectFactory;
import io.restassured.http.Method;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

/** API-to-database F2B core; insert the application's page workflow at the marked step. */
@SpringBootTest(classes = SauceDemoApplication.class)
@ContextConfiguration(classes = SauceDemoApplication.class, inheritLocations = false)
public class FrontToBackExample extends TafBaseTest {
  @Autowired PlaywrightObjectFactory pages;

  @Value("${quickstart.orders-ui-base-url}")
  String ordersUiBaseUrl;

  @Test
  @TestCaseId("QS-F2B-001")
  public void tracesOneOwnedOrderAcrossPublicLayers() {
    RestController api = controller(RestController.class, "orders-api");
    DatabaseController database = controller(DatabaseController.class, "orders-db");
    String traceId = testSession().getCorrelationContext().getTraceId();
    String orderId = null;
    Throwable primaryFailure = null;
    try {
      orderId =
          api.execute(
                  RestRequest.request(Method.POST, "/orders")
                      .header("X-Correlation-Id", traceId)
                      .body("{\"sku\":\"BACKPACK\",\"quantity\":1}", "application/json")
                      .build())
              .assertStatus(201)
              .nativeResponse()
              .jsonPath()
              .getString("id");
      assertNotNull(orderId);

      String displayedOrderId = new OrdersPage(pages).submitAndReadOrderId(ordersUiBaseUrl);
      assertEquals(displayedOrderId, orderId, "UI must display the API-created order");

      String ownedOrderId = orderId;
      var completed =
          AwaitableAssertion.create(
                  "API and database agree on COMPLETE",
                  ignored -> {
                    var apiOrder =
                        api.execute(
                            RestRequest.request(Method.GET, "/orders/{id}")
                                .pathParameter("id", ownedOrderId)
                                .build());
                    if (apiOrder.statusCode() != 200) return false;
                    var rows =
                        database.query(
                            "select status from orders where order_id = ?", ownedOrderId);
                    return "COMPLETE"
                            .equals(apiOrder.nativeResponse().jsonPath().getString("status"))
                        && rows.rowCount() == 1
                        && "COMPLETE".equals(rows.rows().getFirst().get("status"));
                  },
                  Duration.ofSeconds(60),
                  Duration.ofSeconds(1))
              .await();
      assertTrue(completed.isSuccessful(), completed.getMessage());
      assertEquals(
          database
              .query("select count(*) as total from orders where order_id = ?", orderId)
              .rows()
              .getFirst()
              .get("total"),
          1L);
    } catch (RuntimeException | AssertionError failure) {
      primaryFailure = failure;
      throw failure;
    } finally {
      if (orderId != null) {
        try {
          api.execute(
                  RestRequest.request(Method.DELETE, "/orders/{id}")
                      .pathParameter("id", orderId)
                      .header("X-Correlation-Id", traceId)
                      .build())
              .assertStatus(204);
        } catch (RuntimeException | AssertionError cleanupFailure) {
          if (primaryFailure != null) primaryFailure.addSuppressed(cleanupFailure);
          else throw cleanupFailure;
        }
      }
    }
  }
}

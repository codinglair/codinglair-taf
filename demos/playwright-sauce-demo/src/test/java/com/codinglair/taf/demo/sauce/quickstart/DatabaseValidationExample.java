package com.codinglair.taf.demo.sauce.quickstart;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.codinglair.taf.core.annotation.reporting.TestCaseId;
import com.codinglair.taf.database.DatabaseController;
import com.codinglair.taf.database.QueryResult;
import com.codinglair.taf.demo.sauce.SauceDemoApplication;
import com.codinglair.taf.runtime.core.condition.AwaitableAssertion;
import com.codinglair.taf.runtime.testng.TafBaseTest;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

/** Read-only, parameterized database validation example. */
@SpringBootTest(classes = SauceDemoApplication.class)
@ContextConfiguration(classes = SauceDemoApplication.class, inheritLocations = false)
public class DatabaseValidationExample extends TafBaseTest {
  @Test
  @TestCaseId("QS-DB-001")
  public void validatesOneOrderWithoutExposingSensitiveColumns() {
    DatabaseController orders = controller(DatabaseController.class, "orders-db");
    String orderId = System.getProperty("quickstart.order-id", "QS-ORDER-001");

    QueryResult count =
        orders.query("select count(*) as total from orders where order_id = ?", orderId);
    assertEquals(((Number) count.rows().getFirst().get("total")).intValue(), 1);

    QueryResult row =
        orders.query("select order_id, status, quantity from orders where order_id = ?", orderId);
    assertEquals(row.rowCount(), 1);
    Map<String, Object> order = row.rows().getFirst();
    assertEquals(order.get("order_id"), orderId);
    assertEquals(((Number) order.get("quantity")).intValue(), 1);

    var result =
        AwaitableAssertion.create(
                "order reaches READY",
                value -> "READY".equals(value),
                Duration.ofSeconds(30),
                Duration.ofMillis(500))
            .await(
                () ->
                    orders
                        .query("select status from orders where order_id = ?", orderId)
                        .rows()
                        .stream()
                        .findFirst()
                        .map(values -> values.get("status"))
                        .orElse(null));
    assertTrue(result.isSuccessful(), result.getMessage());
  }
}

package __BASE_PACKAGE__.architecture;

import static org.junit.jupiter.api.Assertions.*;

import com.codinglair.taf.runtime.testng.TafBaseTest;
import __BASE_PACKAGE__.functional.ProductFunctionalExample;

class ContractStructureTest {
  @org.junit.jupiter.api.Test
  void frameworkOwnsLifecycleAndReporting() {
    assertTrue(TafBaseTest.class.isAssignableFrom(ProductFunctionalExample.class));
    assertEquals(2, ProductFunctionalExample.class.getDeclaredMethods().length);
  }

  @org.junit.jupiter.api.Test
  void unselectedProductsStayAbsent() {
    assertThrows(
        ClassNotFoundException.class, () -> Class.forName("com.codinglair.taf.mcp.McpServer"));
    assertThrows(
        ClassNotFoundException.class, () -> Class.forName("io.appium.java_client.AppiumDriver"));
  }
}

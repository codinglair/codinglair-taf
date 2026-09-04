package com.codinglair.taf.demo.sauce.architecture;

import static org.junit.jupiter.api.Assertions.*;

import com.codinglair.taf.demo.sauce.functional.SauceDemoLoginTest;
import com.codinglair.taf.demo.sauce.functional.SauceDemoProductTest;
import com.codinglair.taf.runtime.testng.TafBaseTest;
import com.codinglair.taf.runtime.testng.TestNgLifecycleListener;

class ContractStructureTest {
  @org.junit.jupiter.api.Test
  void frameworkOwnsLifecycleAndReporting() {
    assertTrue(TafBaseTest.class.isAssignableFrom(SauceDemoLoginTest.class));
    assertTrue(TafBaseTest.class.isAssignableFrom(SauceDemoProductTest.class));
  }

  @org.junit.jupiter.api.Test
  void unselectedProductsStayAbsent() {
    assertThrows(
        ClassNotFoundException.class, () -> Class.forName("com.codinglair.taf.mcp.McpServer"));
    assertThrows(
        ClassNotFoundException.class, () -> Class.forName("io.appium.java_client.AppiumDriver"));
  }

  @org.junit.jupiter.api.Test
  void testNgObserverCanBeServiceLoaded() {
    assertDoesNotThrow(
        () -> {
          new TestNgLifecycleListener();
        });
  }
}

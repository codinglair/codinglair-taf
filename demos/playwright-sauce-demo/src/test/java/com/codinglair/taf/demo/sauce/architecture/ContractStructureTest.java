package com.codinglair.taf.demo.sauce.architecture;

import static org.junit.jupiter.api.Assertions.*;

import com.codinglair.taf.demo.sauce.functional.SauceDemoLoginTest;
import com.codinglair.taf.demo.sauce.functional.SauceDemoProductTest;
import com.codinglair.taf.demo.sauce.preflight.SauceDemoPreflightTest;
import com.codinglair.taf.runtime.testng.TafBaseTest;
import com.codinglair.taf.runtime.testng.TestNgLifecycleListener;
import java.nio.file.Files;
import java.nio.file.Path;

class ContractStructureTest {
  @org.junit.jupiter.api.Test
  void frameworkOwnsLifecycleAndReporting() {
    assertTrue(TafBaseTest.class.isAssignableFrom(SauceDemoLoginTest.class));
    assertTrue(TafBaseTest.class.isAssignableFrom(SauceDemoProductTest.class));
    assertTrue(TafBaseTest.class.isAssignableFrom(SauceDemoPreflightTest.class));
  }

  @org.junit.jupiter.api.Test
  void defaultSuiteIsDeterministicAndExcludesLiveBrowserTests() throws Exception {
    Path projectRoot = Path.of(System.getProperty("taf.consumer.projectRoot", "."));
    String pom = Files.readString(projectRoot.resolve("pom.xml"));
    String suite =
        Files.readString(projectRoot.resolve("src/test/resources/suites/testng-default.xml"));

    assertTrue(pom.contains("src/test/resources/suites/testng-default.xml"));
    assertTrue(suite.contains(SauceDemoPreflightTest.class.getName()));
    assertFalse(suite.contains(SauceDemoLoginTest.class.getName()));
    assertFalse(suite.contains(SauceDemoProductTest.class.getName()));
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

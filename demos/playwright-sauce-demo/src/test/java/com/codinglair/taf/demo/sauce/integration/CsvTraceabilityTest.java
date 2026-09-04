package com.codinglair.taf.demo.sauce.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codinglair.taf.demo.sauce.model.LoginInput;
import com.codinglair.taf.demo.sauce.model.ProductExpectation;
import com.codinglair.taf.runtime.definition.CsvTestDefinitionConfiguration;
import com.codinglair.taf.runtime.definition.CsvTestDefinitionRepository;
import com.codinglair.taf.runtime.definition.DefinitionResourceLocation;
import com.codinglair.taf.runtime.definition.TestDefinitionResolver;
import java.nio.file.Path;

class CsvTraceabilityTest {
  private final TestDefinitionResolver definitions =
      new TestDefinitionResolver(
          new CsvTestDefinitionRepository(
              new CsvTestDefinitionConfiguration(
                  location("inputs.csv"), location("expected-outputs.csv"), "caseId")));

  @org.junit.jupiter.api.Test
  void representativeIdsResolveToTypedInputAndExpectedOutput() {
    LoginInput login = definitions.requireInput("TC0001", LoginInput.class);
    ProductExpectation product =
        definitions.requireExpectedOutput("TC0002", ProductExpectation.class);
    ProductExpectation purchase =
        definitions.requireExpectedOutput("TC0003", ProductExpectation.class);

    assertEquals("standard_user", login.username());
    assertTrue(login.passwordReference().startsWith("secret://jasypt/"));
    assertEquals("Sauce Labs Backpack", product.productName());
    assertEquals("Thank you for your order!", purchase.productName());
  }

  @org.junit.jupiter.api.Test
  void missingIdHasActionableDiagnostic() {
    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () -> definitions.requireInput("TC-MISSING", LoginInput.class));

    assertTrue(failure.getMessage().contains("TC-MISSING"));
  }

  private static DefinitionResourceLocation location(String filename) {
    String directory = System.getenv("TAF_TEST_DATA_LOCATION");
    return directory == null || directory.isBlank()
        ? DefinitionResourceLocation.classpath("test-data/" + filename)
        : DefinitionResourceLocation.file(Path.of(directory).resolve(filename));
  }
}

package com.codinglair.taf.demo.sauce.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.core.annotation.reporting.TestCaseId;
import com.codinglair.taf.demo.sauce.integration.MongoDefinitionFixture;
import com.codinglair.taf.demo.sauce.model.LoginInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "taf.migration.containers", matches = "true")
@DisplayName("GATE-004C Cucumber Mongo consumer")
class CucumberMongoIntegrationTest {
  @Test
  @DisplayName("resolves the Cucumber case from the migrated Mongo repository")
  @TestCaseId("TC0902")
  void cucumberConsumerDefinitionComesFromMongoWithNoFileFallback() throws Exception {
    try (MongoDefinitionFixture fixture = new MongoDefinitionFixture()) {
      com.codinglair.taf.runtime.definition.TestDefinitionResolver definitions = fixture.start();
      assertThat(definitions.requireInput("TC0003", LoginInput.class).username())
          .isEqualTo("performance_glitch_user");
    }
  }
}

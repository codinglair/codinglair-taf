package com.codinglair.taf.runtime.definition.mongodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("MongoDB repository boundary validation")
class MongoRepositoryValidationTest {
  @Nested
  @DisplayName("Version decoding")
  class VersionDecoding {
    @Test
    @DisplayName("accepts BSON integer, long, and numeric string representations")
    void acceptsSupportedRepresentations() {
      assertThat(MongoTestDefinitionRepository.number(new Document("version", 7), "version"))
          .isEqualTo(7L);
      assertThat(MongoTestDefinitionRepository.number(new Document("version", 8L), "version"))
          .isEqualTo(8L);
      assertThat(MongoTestDefinitionRepository.number(new Document("version", "9"), "version"))
          .isEqualTo(9L);
    }

    @Test
    @DisplayName("distinguishes missing and invalid version values")
    void rejectsUnsupportedRepresentations() {
      assertThatThrownBy(() -> MongoTestDefinitionRepository.number(new Document(), "version"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("version is missing");
      assertThatThrownBy(
              () ->
                  MongoTestDefinitionRepository.number(
                      new Document("version", new Document()), "version"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("org.bson.Document");
    }
  }

  @Nested
  @DisplayName("MongoDB key safety")
  class MongoKeySafety {
    @ParameterizedTest(name = "rejects unsafe value {0}")
    @ValueSource(strings = {"trace.value", "$trace", "trace\u0000value", "trace\nvalue"})
    @DisplayName("rejects path operators and control characters")
    void rejectsUnsafeCharacters(String value) {
      assertThatThrownBy(() -> MongoTestDefinitionRepository.normalize(value, "name"))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects values longer than the bounded identifier limit")
    void rejectsOversizedValues() {
      assertThatThrownBy(() -> MongoTestDefinitionRepository.normalize("x".repeat(129), "name"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("128");
    }
  }
}

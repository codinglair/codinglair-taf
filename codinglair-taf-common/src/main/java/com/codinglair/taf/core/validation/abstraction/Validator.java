package com.codinglair.taf.core.validation.abstraction;

/**
 * Generic validator abstraction for type-safe validation.
 *
 * @param <T> The type of value being validated
 */
public interface Validator<T> {

  /**
   * Validates the given value.
   *
   * @param expected The expected value
   * @param actual The actual value to validate
   */
  void validate(T expected, T actual);

  /**
   * Validates the given value and returns a result.
   *
   * @param expected The expected value
   * @param actual The actual value to validate
   * @return Validation result
   */
  com.codinglair.taf.core.validation.ValidationResult validateAndGetResult(T expected, T actual);
}

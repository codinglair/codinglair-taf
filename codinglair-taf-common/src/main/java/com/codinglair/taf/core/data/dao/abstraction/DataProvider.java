package com.codinglair.taf.core.data.dao.abstraction;

import java.util.Optional;

/**
 * Abstraction for data providers that supply test data.
 *
 * @param <I> The type of input data
 * @param <O> The type of output data
 */
public interface DataProvider<I, O> {

  /**
   * Retrieves input data for a given key.
   *
   * @param key The data key
   * @return Optional containing the input data
   */
  Optional<I> getInput(String key);

  /**
   * Retrieves expected output data for a given key.
   *
   * @param key The data key
   * @return Optional containing the expected output
   */
  Optional<O> getExpectedOutput(String key);

  /**
   * Checks if data exists for the given key.
   *
   * @param key The data key
   * @return true if data exists
   */
  boolean hasData(String key);

  /**
   * Gets all available data keys.
   *
   * @return Iterable of data keys
   */
  Iterable<String> getAllKeys();
}

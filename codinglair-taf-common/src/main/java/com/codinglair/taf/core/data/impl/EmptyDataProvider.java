package com.codinglair.taf.core.data.impl;

import com.codinglair.taf.core.data.dao.abstraction.DataProvider;
import java.util.Collections;
import java.util.Optional;

/**
 * Empty data provider implementation for testing.
 *
 * @param <I> The type of input data
 * @param <O> The type of output data
 */
public class EmptyDataProvider<I, O> implements DataProvider<I, O> {

  @Override
  public Optional<I> getInput(String key) {
    return Optional.empty();
  }

  @Override
  public Optional<O> getExpectedOutput(String key) {
    return Optional.empty();
  }

  @Override
  public boolean hasData(String key) {
    return false;
  }

  @Override
  public Iterable<String> getAllKeys() {
    return Collections.emptyList();
  }
}

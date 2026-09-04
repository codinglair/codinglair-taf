package com.codinglair.taf.runtime.definition;

import java.util.Objects;

/** Runner-neutral case-ID resolution entry point shared by TestNG and Cucumber. */
public final class TestDefinitionResolver {
  private final TestDefinitionRepository repository;

  public TestDefinitionResolver(TestDefinitionRepository repository) {
    this.repository = Objects.requireNonNull(repository, "repository");
  }

  public <I, E> TestDefinition<I, E> require(
      String caseId, Class<I> inputType, Class<E> expectedOutputType) {
    return repository.require(caseId, inputType, expectedOutputType);
  }

  public <I> I requireInput(String caseId, Class<I> inputType) {
    return repository.requireInput(caseId, inputType);
  }

  public <E> E requireExpectedOutput(String caseId, Class<E> expectedOutputType) {
    return repository.requireExpectedOutput(caseId, expectedOutputType);
  }
}

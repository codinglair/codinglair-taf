package com.codinglair.taf.runtime.definition;

import java.util.List;

/** Pluggable authoritative source for immutable test definitions. */
public interface TestDefinitionRepository {
  <I, E> TestDefinition<I, E> require(
      String caseId, Class<I> inputType, Class<E> expectedOutputType);

  /** Creates a first immutable version or appends the exact next version. */
  default <I, E> VersionedTestDefinition<I, E> save(
      VersionedTestDefinition<I, E> definition,
      long expectedLatestVersion,
      RepositoryAuthority authority) {
    throw new UnsupportedOperationException("This repository is read-only");
  }

  /** Appends a new immutable version representing an allowed lifecycle transition. */
  default <I, E> VersionedTestDefinition<I, E> transition(
      String caseId,
      DefinitionState target,
      long expectedLatestVersion,
      RepositoryAuthority authority,
      Class<I> inputType,
      Class<E> expectedOutputType) {
    throw new UnsupportedOperationException("This repository is read-only");
  }

  /** Finds latest definitions whose explicit correlation metadata contains the exact pair. */
  default <I, E> List<VersionedTestDefinition<I, E>> findByCorrelation(
      String name, String value, Class<I> inputType, Class<E> expectedOutputType) {
    throw new UnsupportedOperationException("Correlation lookup is not supported");
  }

  /** Resolves only the typed input while retaining paired-definition validation. */
  default <I> I requireInput(String caseId, Class<I> inputType) {
    return require(caseId, inputType, Object.class).input();
  }

  /** Resolves only the typed expected output while retaining paired-definition validation. */
  default <E> E requireExpectedOutput(String caseId, Class<E> expectedOutputType) {
    return require(caseId, Object.class, expectedOutputType).expectedOutput();
  }
}

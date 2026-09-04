package com.codinglair.taf.runtime.definition;

import java.nio.file.Path;

class JsonFileTestDefinitionRepositoryTest extends TestDefinitionRepositoryContract {
  @Override
  protected TestDefinitionRepository repository(Path file) {
    return new FileTestDefinitionRepository(
        new FileTestDefinitionConfiguration(file, DefinitionFileFormat.JSON, AUTHORITY));
  }

  @Override
  protected String extension() {
    return "json";
  }

  @Override
  protected void assertStableRoundTrip(
      Path repositoryFile,
      TestDefinitionRepository repository,
      VersionedTestDefinition<Input, Expected> definition)
      throws Exception {
    assertStableFileRoundTrip(repositoryFile, repository, definition);
  }
}

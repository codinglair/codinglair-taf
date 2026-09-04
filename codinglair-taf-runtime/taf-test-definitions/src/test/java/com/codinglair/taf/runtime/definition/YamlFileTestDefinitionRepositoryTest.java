package com.codinglair.taf.runtime.definition;

import java.nio.file.Path;

class YamlFileTestDefinitionRepositoryTest extends TestDefinitionRepositoryContract {
  @Override
  protected TestDefinitionRepository repository(Path file) {
    return new FileTestDefinitionRepository(
        new FileTestDefinitionConfiguration(file, DefinitionFileFormat.YAML, AUTHORITY));
  }

  @Override
  protected String extension() {
    return "yaml";
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

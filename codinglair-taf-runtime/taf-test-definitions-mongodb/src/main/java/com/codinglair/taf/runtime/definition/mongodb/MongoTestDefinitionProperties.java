package com.codinglair.taf.runtime.definition.mongodb;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Reserved framework-context MongoDB configuration. */
@Validated
@ConfigurationProperties("taf.context.mongodb")
public class MongoTestDefinitionProperties {
  private String uri = "mongodb://localhost:27017";
  private String database = "taf-context";
  private String collection = "test-definitions";
  private String project = "default";
  private String authority = "mongodb";
  private int schemaVersion = 1;
  private boolean bootstrapIndexes = true;
  private boolean requireMigration;

  public String getUri() {
    return uri;
  }

  public void setUri(String uri) {
    this.uri = requireText(uri, "uri");
  }

  public String getDatabase() {
    return database;
  }

  public void setDatabase(String database) {
    this.database = requireIdentifier(database, "database");
  }

  public String getCollection() {
    return collection;
  }

  public void setCollection(String collection) {
    this.collection = requireIdentifier(collection, "collection");
  }

  public String getProject() {
    return project;
  }

  public void setProject(String project) {
    this.project = requireIdentifier(project, "project");
  }

  public String getAuthority() {
    return authority;
  }

  public void setAuthority(String authority) {
    this.authority = requireText(authority, "authority");
  }

  public int getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(int schemaVersion) {
    if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be positive");
    this.schemaVersion = schemaVersion;
  }

  public boolean isBootstrapIndexes() {
    return bootstrapIndexes;
  }

  public void setBootstrapIndexes(boolean bootstrapIndexes) {
    this.bootstrapIndexes = bootstrapIndexes;
  }

  public boolean isRequireMigration() {
    return requireMigration;
  }

  public void setRequireMigration(boolean requireMigration) {
    this.requireMigration = requireMigration;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(name + " must not be blank");
    return value.trim();
  }

  private static String requireIdentifier(String value, String name) {
    String result = requireText(value, name);
    if (!result.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}")) {
      throw new IllegalArgumentException(name + " is invalid");
    }
    return result;
  }
}

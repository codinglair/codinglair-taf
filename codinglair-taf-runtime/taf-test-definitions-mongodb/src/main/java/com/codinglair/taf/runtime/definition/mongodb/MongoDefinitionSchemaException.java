package com.codinglair.taf.runtime.definition.mongodb;

/** Indicates persisted context data that requires an explicit MIG-002 migration. */
public final class MongoDefinitionSchemaException extends IllegalStateException {
  public MongoDefinitionSchemaException(String message) {
    super(message);
  }
}

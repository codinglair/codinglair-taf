package com.codinglair.taf.runtime.definition;

/** Actionable boundary failure while loading or resolving test definitions. */
public final class DefinitionDiagnosticException extends IllegalArgumentException {
  public enum Kind {
    MISSING,
    DUPLICATE,
    MALFORMED,
    TYPE_CONVERSION,
    SECRET_INGESTION
  }

  private final Kind kind;
  private final String caseId;

  public DefinitionDiagnosticException(Kind kind, String caseId, String message) {
    this(kind, caseId, message, null);
  }

  public DefinitionDiagnosticException(Kind kind, String caseId, String message, Throwable cause) {
    super(message, cause);
    this.kind = kind;
    this.caseId = caseId;
  }

  public Kind kind() {
    return kind;
  }

  public String caseId() {
    return caseId;
  }
}

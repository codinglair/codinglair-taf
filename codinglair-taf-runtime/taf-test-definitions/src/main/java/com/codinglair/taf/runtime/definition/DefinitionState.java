package com.codinglair.taf.runtime.definition;

/** Review lifecycle of an immutable test-definition version. */
public enum DefinitionState {
  DRAFT,
  REVIEW,
  APPROVED,
  SUPERSEDED,
  ARCHIVED;

  public boolean canTransitionTo(DefinitionState target) {
    return switch (this) {
      case DRAFT -> target == REVIEW || target == ARCHIVED;
      case REVIEW -> target == DRAFT || target == APPROVED || target == ARCHIVED;
      case APPROVED -> target == SUPERSEDED || target == ARCHIVED;
      case SUPERSEDED -> target == ARCHIVED;
      case ARCHIVED -> false;
    };
  }
}

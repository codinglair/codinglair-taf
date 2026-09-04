package com.codinglair.taf.runtime.core.reporting.abstraction;

/** Audience-aware levels in the consumer reporting hierarchy. */
public enum ReportLevel {
  TEST,
  SCENARIO,
  WORKFLOW,
  PAGE,
  COMPONENT,
  API,
  SCREEN,
  BDD_STEP,
  CONSUMER_ACTION,
  CONTROLLER_OPERATION,
  VALIDATION,
  EVIDENCE
}

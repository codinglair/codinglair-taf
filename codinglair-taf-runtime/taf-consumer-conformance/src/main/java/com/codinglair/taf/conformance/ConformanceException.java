package com.codinglair.taf.conformance;

/** Build-friendly failure containing only paths and sanitized structural diagnostics. */
public final class ConformanceException extends IllegalStateException {
  private final ConformanceReport report;

  public ConformanceException(ConformanceReport report) {
    super(report.violations().size() + " consumer blueprint conformance violation(s)");
    this.report = report;
  }

  public ConformanceReport report() {
    return report;
  }
}

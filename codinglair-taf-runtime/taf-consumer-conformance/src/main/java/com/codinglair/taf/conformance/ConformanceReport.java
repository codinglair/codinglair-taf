package com.codinglair.taf.conformance;

import java.util.List;
import java.util.Objects;

/** Immutable result suitable for build, CLI, or future governed workflow integration. */
public record ConformanceReport(String blueprintVersion, List<ConformanceViolation> violations) {
  public ConformanceReport {
    blueprintVersion = Objects.requireNonNull(blueprintVersion, "blueprintVersion");
    violations = List.copyOf(violations);
  }

  public boolean conforms() {
    return violations.isEmpty();
  }

  public void throwIfInvalid() {
    if (!conforms()) throw new ConformanceException(this);
  }
}

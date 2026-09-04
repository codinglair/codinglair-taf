package com.codinglair.taf.runtime.core.preflight;

import java.util.List;

/** Supplies sanitized checks to the Runtime execution preflight boundary. */
@FunctionalInterface
public interface ConsumerPreflightContributor {
  List<PreflightDiagnostic> inspect();
}

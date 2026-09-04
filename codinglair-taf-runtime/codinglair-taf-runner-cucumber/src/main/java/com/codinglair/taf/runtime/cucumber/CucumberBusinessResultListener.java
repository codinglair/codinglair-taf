package com.codinglair.taf.runtime.cucumber;

/** Receives a completed business-facing Cucumber result. */
@FunctionalInterface
public interface CucumberBusinessResultListener {
  void onResult(CucumberBusinessResult result);
}

package com.codinglair.taf.runtime.cucumber;

/** A business-readable Gherkin step result. */
public record BusinessStepResult(String keyword, String text, String status) {}

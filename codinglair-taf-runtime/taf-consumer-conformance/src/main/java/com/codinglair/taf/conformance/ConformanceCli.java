package com.codinglair.taf.conformance;

import java.nio.file.Path;

/** Minimal CLI entry point: zero on conformance, two on validation failure. */
public final class ConformanceCli {
  private ConformanceCli() {}

  public static void main(String[] args) {
    if (args.length != 1)
      throw new IllegalArgumentException("Usage: ConformanceCli <consumer-project-directory>");
    ConformanceReport report = new ConsumerProjectValidator().validate(Path.of(args[0]));
    report
        .violations()
        .forEach(
            v ->
                System.err.println(
                    v.rule() + " " + v.location() + ": " + v.message() + "; " + v.correction()));
    if (!report.conforms()) System.exit(2);
  }
}

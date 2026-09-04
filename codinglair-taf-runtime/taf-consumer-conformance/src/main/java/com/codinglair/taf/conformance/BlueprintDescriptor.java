package com.codinglair.taf.conformance;

import java.util.List;

/** Version 1 descriptor selection metadata. Environment values deliberately do not belong here. */
public record BlueprintDescriptor(
    String schemaVersion,
    Project project,
    RuntimeSelection runtime,
    List<String> capabilities,
    List<String> runners,
    TestDefinitions testDefinitions,
    Reporting reporting) {
  public record Project(String name, String type, String basePackage) {}

  public record RuntimeSelection(String version) {}

  public record TestDefinitions(String provider, boolean authoritative) {}

  public record Reporting(String adapter) {}
}

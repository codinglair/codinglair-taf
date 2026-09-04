package com.codinglair.taf.runtime.environment;

import java.util.Map;
import java.util.Objects;

/** Exports provisioned endpoints without coupling the environment module to Spring test APIs. */
public final class DynamicPropertyExporter {
  private DynamicPropertyExporter() {}

  public static void export(EnvironmentResource resource, DynamicPropertySink sink) {
    Objects.requireNonNull(resource, "resource");
    Objects.requireNonNull(sink, "sink");
    for (Map.Entry<String, String> property : resource.properties().entrySet()) {
      String value = property.getValue();
      sink.add(property.getKey(), () -> value);
    }
  }
}

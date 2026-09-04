package com.codinglair.taf.database;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class SutConnectionRegistry {
  private final Map<String, SutConnectionDescriptor> connections;

  public SutConnectionRegistry(
      Collection<SutConnectionDescriptor> descriptors, String contextJdbcUrl) {
    Map<String, SutConnectionDescriptor> values = new LinkedHashMap<>();
    for (SutConnectionDescriptor descriptor : descriptors) {
      String key = descriptor.name().toLowerCase(Locale.ROOT);
      if (values.putIfAbsent(key, descriptor) != null)
        throw new IllegalArgumentException(
            "Duplicate or ambiguous SUT connection name: " + descriptor.name());
      if (contextJdbcUrl != null
          && canonical(contextJdbcUrl).equals(canonical(descriptor.jdbcUrl())))
        throw new IllegalArgumentException(
            "TAF context and SUT connection identities must not alias: " + descriptor.name());
    }
    connections = Map.copyOf(values);
  }

  public SutConnectionDescriptor require(String name) {
    if (name == null || name.isBlank())
      throw new IllegalArgumentException("SUT connection name is required");
    SutConnectionDescriptor descriptor = connections.get(name.toLowerCase(Locale.ROOT));
    if (descriptor == null) throw new IllegalArgumentException("Unknown SUT connection: " + name);
    return descriptor;
  }

  public Collection<SutConnectionDescriptor> connections() {
    return connections.values();
  }

  private static String canonical(String value) {
    return value.trim().toLowerCase(Locale.ROOT);
  }
}

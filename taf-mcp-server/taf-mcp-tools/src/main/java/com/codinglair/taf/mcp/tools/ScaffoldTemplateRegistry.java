package com.codinglair.taf.mcp.tools;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe, immutable-template registry keyed by exact name and version. */
public final class ScaffoldTemplateRegistry {
  private final Map<Key, ScaffoldTemplate> templates = new ConcurrentHashMap<>();

  public ScaffoldTemplateRegistry(Collection<ScaffoldTemplate> templates) {
    Objects.requireNonNull(templates, "templates").forEach(this::register);
  }

  public ScaffoldTemplate require(String name, String version) {
    var template = templates.get(new Key(name, version));
    if (template == null) throw new IllegalArgumentException("template version is unavailable");
    return template;
  }

  private void register(ScaffoldTemplate template) {
    Objects.requireNonNull(template, "template");
    if (templates.putIfAbsent(new Key(template.name(), template.version()), template) != null) {
      throw new IllegalArgumentException("duplicate template version");
    }
  }

  private record Key(String name, String version) {}
}

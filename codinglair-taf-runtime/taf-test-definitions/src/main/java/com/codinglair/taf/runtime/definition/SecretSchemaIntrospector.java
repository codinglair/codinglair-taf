package com.codinglair.taf.runtime.definition;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Deterministically discovers explicit secret metadata without name heuristics. */
public final class SecretSchemaIntrospector {
  private SecretSchemaIntrospector() {}

  public static Map<String, SecretFieldDefinition> inspect(Class<?> type) {
    Objects.requireNonNull(type, "type");
    Map<String, SecretFieldDefinition> result = new LinkedHashMap<>();
    for (Class<?> current = type;
        current != null && current != Object.class;
        current = current.getSuperclass()) {
      for (Field field : current.getDeclaredFields()) {
        add(result, field.getName(), field.getAnnotation(SecretField.class));
      }
      for (Method method : current.getDeclaredMethods()) {
        if (method.getParameterCount() == 0) {
          add(result, method.getName(), method.getAnnotation(SecretField.class));
        }
      }
    }
    if (type.isRecord()) {
      for (RecordComponent component : type.getRecordComponents()) {
        add(result, component.getName(), component.getAnnotation(SecretField.class));
      }
    }
    return Map.copyOf(result);
  }

  private static void add(
      Map<String, SecretFieldDefinition> result, String name, SecretField annotation) {
    if (annotation == null) return;
    SecretFieldDefinition definition =
        new SecretFieldDefinition(annotation.kind(), annotation.required());
    SecretFieldDefinition existing = result.putIfAbsent(name, definition);
    if (existing != null && !existing.equals(definition)) {
      throw new IllegalArgumentException("Conflicting secret classification for '" + name + "'");
    }
  }
}

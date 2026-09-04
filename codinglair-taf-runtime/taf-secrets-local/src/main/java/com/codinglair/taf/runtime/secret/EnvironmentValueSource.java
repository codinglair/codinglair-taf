package com.codinglair.taf.runtime.secret;

@FunctionalInterface
public interface EnvironmentValueSource {
  String get(String name);
}

package com.codinglair.taf.runtime.environment;

import java.util.Map;

interface ManagedContainer {
  void start();

  void stop();

  boolean isRunning();

  Map<String, String> properties();

  String logs();
}

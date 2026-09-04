package com.codinglair.taf.core.reporting.factory;

import com.codinglair.taf.core.reporting.abstraction.TestReporter;
import java.lang.reflect.Constructor;

/** Factory for creating TestReporter instances. */
public class ReporterFactory {

  /**
   * Creates a reporter by class name.
   *
   * @param reporterClassName The fully qualified class name of the reporter
   * @return The created reporter instance
   */
  public static <T extends TestReporter> T create(String reporterClassName) {
    try {
      Class<?> clazz = Class.forName(reporterClassName);
      Constructor<?> constructor = clazz.getDeclaredConstructor();
      return (T) constructor.newInstance();
    } catch (Exception e) {
      throw new RuntimeException("Failed to create reporter: " + reporterClassName, e);
    }
  }
}

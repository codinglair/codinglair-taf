package com.codinglair.taf.runtime.core.lifecycle;

import com.codinglair.taf.runtime.core.TestSession;

/** Adds capability-specific registrations to each newly created test session. */
@FunctionalInterface
public interface TestSessionConfigurer {
  void configure(TestSession session);
}

package com.codinglair.taf.runtime.core.lifecycle;

import com.codinglair.taf.runtime.core.TestSession;

/** Creates a new isolated session for a lifecycle-owner invocation. */
@FunctionalInterface
public interface TestSessionFactory {
  TestSession create();
}

package com.codinglair.taf.runtime.core.lifecycle;

import com.codinglair.taf.runtime.core.TestSession;
import com.codinglair.taf.runtime.core.context.TestContext;
import com.codinglair.taf.runtime.core.controller.TestController;

/**
 * Stable consumer-bean access to state already bound to the current test invocation.
 *
 * <p>The accessor is safe to retain in a singleton. Every operation resolves the current session at
 * call time and never creates or caches invocation-specific state.
 */
public interface SessionAwareAccessor {
  /** Returns the session bound to the calling thread or fails with an actionable diagnostic. */
  TestSession session();

  /** Returns the context belonging to the currently bound session. */
  default TestContext context() {
    return session().getTestContext();
  }

  /** Resolves and lazily initializes a typed, named controller in the current session. */
  default <T extends TestController> T controller(Class<T> type, String name) {
    return session().getController(type, name);
  }
}

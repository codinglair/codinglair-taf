package com.codinglair.taf.runtime.core.lifecycle;

import com.codinglair.taf.runtime.core.TestSession;
import java.util.Optional;

/** Resolves the session already bound to the current invocation; it never creates one. */
public interface CurrentTestSession {
  Optional<TestSession> find();

  default TestSession require() {
    TestSession session =
        find()
            .orElseThrow(
                () ->
                    new LifecycleViolationException(
                        "No TestSession is bound to the current invocation; capability calls require "
                            + "a framework-owned TestNG method or Cucumber scenario"));
    if (session.isClosed()) {
      throw new LifecycleViolationException(
          "the TestSession bound to the current invocation is closed (session="
              + session.getSessionId()
              + ")");
    }
    return session;
  }
}

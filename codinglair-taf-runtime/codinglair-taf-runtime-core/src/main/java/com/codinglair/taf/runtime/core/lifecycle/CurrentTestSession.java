package com.codinglair.taf.runtime.core.lifecycle;

import com.codinglair.taf.runtime.core.TestSession;
import java.util.Optional;

/** Resolves the session already bound to the current invocation; it never creates one. */
public interface CurrentTestSession {
  Optional<TestSession> find();

  default TestSession require() {
    return find()
        .orElseThrow(
            () -> new IllegalStateException("No TestSession is bound to the current invocation"));
  }
}

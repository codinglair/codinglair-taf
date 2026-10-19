package com.codinglair.taf.runtime.core.lifecycle;

import com.codinglair.taf.runtime.core.TestSession;
import java.util.Objects;

/** Default resolving-only {@link SessionAwareAccessor}. */
public final class DefaultSessionAwareAccessor implements SessionAwareAccessor {
  private final CurrentTestSession currentSession;

  public DefaultSessionAwareAccessor(CurrentTestSession currentSession) {
    this.currentSession = Objects.requireNonNull(currentSession, "currentSession");
  }

  @Override
  public TestSession session() {
    return currentSession.require();
  }
}

package com.codinglair.taf.messaging.aws.common;

import java.time.Duration;
import java.time.Instant;

/** Injectable time boundary used by bounded AWS operations. */
public interface AwsClock {
  Instant now();

  void sleep(Duration duration) throws InterruptedException;

  static AwsClock system() {
    return SystemAwsClock.INSTANCE;
  }

  enum SystemAwsClock implements AwsClock {
    INSTANCE;

    public Instant now() {
      return Instant.now();
    }

    public void sleep(Duration duration) throws InterruptedException {
      Thread.sleep(duration);
    }
  }
}

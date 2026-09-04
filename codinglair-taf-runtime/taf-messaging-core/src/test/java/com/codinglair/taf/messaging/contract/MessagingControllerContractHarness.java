package com.codinglair.taf.messaging.contract;

import com.codinglair.taf.messaging.MessagingController;
import java.time.Duration;

/** Adapter-owned fixture used by the reusable messaging contract suite. */
public interface MessagingControllerContractHarness extends AutoCloseable {
  MessagingController controller();

  void awaitConsumeStarted(Duration timeout) throws InterruptedException;

  boolean isClosed();

  @Override
  void close();
}

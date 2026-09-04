package com.codinglair.taf.runtime.core.reporting.impl.allure;

import java.util.Optional;

/**
 * Process-local bridge between Spring-bound adapter configuration and TestNG's terminal callback.
 */
final class AllureSingleFileRunCoordinator {
  private static Registration registration;
  private static boolean executionActive;

  private AllureSingleFileRunCoordinator() {}

  static synchronized void register(AllureSingleFilePublisher publisher) {
    if (registration == null) {
      registration = new Registration(publisher, publisher.configurationKey(), 1);
      return;
    }
    if (!registration.configurationKey.equals(publisher.configurationKey())) {
      throw new IllegalStateException(
          "Conflicting enabled Allure single-file publishers are configured for one test process");
    }
    registration =
        new Registration(
            registration.publisher, registration.configurationKey, registration.references + 1);
  }

  static synchronized void unregister(AllureSingleFilePublisher publisher) {
    if (registration == null
        || !registration.configurationKey.equals(publisher.configurationKey())) {
      return;
    }
    if (executionActive) {
      return;
    }
    registration =
        registration.references == 1
            ? null
            : new Registration(
                registration.publisher, registration.configurationKey, registration.references - 1);
  }

  static synchronized void executionStarted() {
    executionActive = true;
  }

  static Optional<java.nio.file.Path> executionFinished() {
    AllureSingleFilePublisher publisher;
    synchronized (AllureSingleFileRunCoordinator.class) {
      publisher = registration == null ? null : registration.publisher;
    }
    try {
      return publisher == null ? Optional.empty() : publisher.publishOnce();
    } finally {
      synchronized (AllureSingleFileRunCoordinator.class) {
        registration = null;
        executionActive = false;
      }
    }
  }

  private record Registration(
      AllureSingleFilePublisher publisher, String configurationKey, int references) {}
}

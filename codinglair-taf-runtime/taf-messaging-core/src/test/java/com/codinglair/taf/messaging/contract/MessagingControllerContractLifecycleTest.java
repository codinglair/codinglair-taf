package com.codinglair.taf.messaging.contract;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Messaging controller contract lifecycle")
class MessagingControllerContractLifecycleTest {
  @Test
  @DisplayName("cleanup does not mask a harness creation failure")
  void cleanupAfterHarnessCreationFailure() {
    var contract =
        new MessagingControllerContract() {
          @Override
          protected MessagingControllerContractHarness createHarness() {
            throw new IllegalStateException("fixture initialization failed");
          }
        };

    assertThrows(IllegalStateException.class, contract::createController);
    assertThatCode(contract::closeController).doesNotThrowAnyException();
  }
}

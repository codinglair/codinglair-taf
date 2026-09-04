package com.codinglair.taf.mobile.appium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.runtime.core.controller.ControllerState;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Android controller lifecycle")
class AndroidControllerLifecycleTest {
  @Nested
  @DisplayName("Without a device")
  class DeviceFreeBehavior {
    @Test
    @DisplayName("fails initialization with structured corrective action and closes idempotently")
    void initializationFailureIsStructuredAndCleanupIsIdempotent() {
      var controller =
          new DefaultAndroidController(
              "primary",
              AndroidControllerSettingsTest.valid(),
              settings -> {
                throw new IOException("endpoint unavailable");
              });
      assertThatThrownBy(() -> controller.initialize(TestContexts.context()))
          .isInstanceOf(AndroidControllerException.class)
          .satisfies(
              failure -> {
                var structured = (AndroidControllerException) failure;
                assertThat(structured.operation()).isEqualTo("initialize");
                assertThat(structured.correctiveAction()).contains("UiAutomator2");
              });
      assertThat(controller.state()).isEqualTo(ControllerState.FAILED);
      controller.close();
      controller.close();
      assertThat(controller.state()).isEqualTo(ControllerState.CLOSED);
    }

    @Test
    @DisplayName("rejects operations before initialization")
    void rejectsEarlyOperation() {
      var controller =
          new DefaultAndroidController(
              "primary",
              AndroidControllerSettingsTest.valid(),
              AppiumAndroidSessionFactory.standard());
      assertThatThrownBy(controller::nativeDriver)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("NEW");
    }
  }
}

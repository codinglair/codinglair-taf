package com.codinglair.taf.mobile.appium;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("Android Appium auto-configuration")
class AndroidAutoConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  TafRuntimeAutoConfiguration.class, AndroidAutoConfiguration.class));

  @Test
  @DisplayName("is disabled by default")
  void disabledByDefault() {
    runner.run(context -> assertThat(context).doesNotHaveBean(AndroidControllerFactory.class));
  }

  @Test
  @DisplayName("registers lazy named controllers without opening device sessions")
  void registersNamedControllersLazily() {
    runner
        .withPropertyValues(
            "taf.mobile.android.enabled=true",
            "taf.mobile.android.controllers.device.server-url=http://127.0.0.1:4723",
            "taf.mobile.android.controllers.device.device-name=emulator",
            "taf.mobile.android.controllers.device.app-package=com.example.app")
        .run(
            context -> {
              assertThat(context).hasSingleBean(AndroidControllerFactory.class);
              try (var session = context.getBean(TestSessionFactory.class).create()) {
                assertThat(
                        session
                            .getControllerRegistry()
                            .hasController(AndroidController.class, "device"))
                    .isTrue();
              }
            });
  }

  @Test
  @DisplayName("reports invalid configuration through aggregated preflight")
  void reportsInvalidConfiguration() {
    runner
        .withPropertyValues("taf.mobile.android.enabled=true")
        .run(
            context ->
                assertThat(
                        context
                            .getBean(
                                com.codinglair.taf.runtime.core.preflight.ConsumerPreflight.class)
                            .inspect()
                            .diagnostics())
                    .anyMatch(value -> value.checkId().equals("mobile-android.default")));
  }
}

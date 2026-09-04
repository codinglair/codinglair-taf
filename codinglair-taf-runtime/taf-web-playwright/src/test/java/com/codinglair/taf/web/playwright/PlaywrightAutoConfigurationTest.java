package com.codinglair.taf.web.playwright;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflight;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PlaywrightAutoConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(PlaywrightAutoConfiguration.class));

  @Test
  void disabledByDefault() {
    runner.run(context -> assertThat(context).doesNotHaveBean(PlaywrightControllerFactory.class));
  }

  @Test
  void enabledCreatesFactoryButNotMutableController() {
    runner
        .withPropertyValues("taf.web.playwright.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(PlaywrightControllerFactory.class);
              assertThat(context).doesNotHaveBean(PlaywrightController.class);
            });
  }

  @Test
  void consumerFactoryWins() {
    PlaywrightControllerFactory custom = name -> null;
    runner
        .withBean(PlaywrightControllerFactory.class, () -> custom)
        .withPropertyValues("taf.web.playwright.enabled=true")
        .run(
            context ->
                assertThat(context.getBean(PlaywrightControllerFactory.class)).isSameAs(custom));
  }

  @Test
  void invalidRemoteConfigurationLoadsAndFailsAggregatedPreflightBeforeBrowserStartup() {
    runner
        .withConfiguration(AutoConfigurations.of(TafRuntimeAutoConfiguration.class))
        .withPropertyValues(
            "taf.web.playwright.enabled=true", "taf.web.playwright.controllers.remote.mode=remote")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(ConsumerPreflight.class).inspect().diagnostics())
                  .anyMatch(check -> check.checkId().equals("web-playwright.remote"));
            });
  }

  @Test
  void namedControllersAreRegisteredIntoEverySessionWithoutStartingBrowser() {
    var created = new CopyOnWriteArrayList<PlaywrightController>();
    runner
        .withConfiguration(AutoConfigurations.of(TafRuntimeAutoConfiguration.class))
        .withBean(
            PlaywrightControllerFactory.class,
            () ->
                name -> {
                  PlaywrightController controller =
                      new DefaultPlaywrightController(name, new PlaywrightControllerSettings());
                  created.add(controller);
                  return controller;
                })
        .withPropertyValues(
            "taf.web.playwright.enabled=true",
            "taf.web.playwright.controllers.customer.base-url=https://example.test")
        .run(
            context -> {
              TestSessionFactory sessions = context.getBean(TestSessionFactory.class);
              try (var first = sessions.create();
                  var second = sessions.create()) {
                assertThat(
                        first
                            .getControllerRegistry()
                            .hasController(PlaywrightController.class, "customer"))
                    .isTrue();
                assertThat(
                        second
                            .getControllerRegistry()
                            .hasController(PlaywrightController.class, "customer"))
                    .isTrue();
                assertThat(created).hasSize(2);
                assertThat(created.get(0)).isNotSameAs(created.get(1));
                assertThat(created)
                    .allMatch(
                        controller ->
                            controller.state()
                                == com.codinglair.taf.runtime.core.controller.ControllerState.NEW);
              }
            });
  }
}

package com.codinglair.taf.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.api.rest.RestAutoConfiguration;
import com.codinglair.taf.api.rest.RestController;
import com.codinglair.taf.api.rest.RestControllerFactory;
import com.codinglair.taf.database.DatabaseAutoConfiguration;
import com.codinglair.taf.database.DatabaseController;
import com.codinglair.taf.database.SutConnectionRegistry;
import com.codinglair.taf.mobile.appium.AndroidAutoConfiguration;
import com.codinglair.taf.mobile.appium.AndroidController;
import com.codinglair.taf.mobile.appium.AndroidControllerFactory;
import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import com.codinglair.taf.web.playwright.PlaywrightAutoConfiguration;
import com.codinglair.taf.web.playwright.PlaywrightController;
import com.codinglair.taf.web.playwright.PlaywrightControllerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("Top-level starter contexts")
class TopLevelStarterContextTest {
  @Nested
  @DisplayName("Web starter isolation")
  class WebStarterIsolation {
    private final ApplicationContextRunner runner =
        starterRunner(PlaywrightAutoConfiguration.class);

    @Test
    @DisplayName("web starter acquires only the configured lazy Playwright controller")
    void webStarter() {
      runner
          .withPropertyValues(
              "taf.web.playwright.enabled=true",
              "taf.web.playwright.controllers.browser.base-url=https://example.test")
          .run(
              context -> {
                assertThat(context)
                    .hasSingleBean(TestSessionFactory.class)
                    .hasSingleBean(PlaywrightControllerFactory.class);
                assertUnrelatedCapabilitiesAreAbsent(context, "web");
                try (var session = context.getBean(TestSessionFactory.class).create()) {
                  assertThat(
                          session
                              .getControllerRegistry()
                              .hasController(PlaywrightController.class, "browser"))
                      .isTrue();
                }
              });
    }
  }

  @Nested
  @DisplayName("API starter isolation")
  class ApiStarterIsolation {
    private final ApplicationContextRunner runner = starterRunner(RestAutoConfiguration.class);

    @Test
    @DisplayName("API starter acquires only the configured lazy REST controller")
    void apiStarter() {
      runner
          .withPropertyValues(
              "taf.api.rest.enabled=true",
              "taf.api.rest.controllers.catalog.base-url=https://example.test")
          .run(
              context -> {
                assertThat(context)
                    .hasSingleBean(TestSessionFactory.class)
                    .hasSingleBean(RestControllerFactory.class);
                assertUnrelatedCapabilitiesAreAbsent(context, "api");
                try (var session = context.getBean(TestSessionFactory.class).create()) {
                  assertThat(
                          session
                              .getControllerRegistry()
                              .hasController(RestController.class, "catalog"))
                      .isTrue();
                }
              });
    }
  }

  @Nested
  @DisplayName("Database starter isolation")
  class DatabaseStarterIsolation {
    private final ApplicationContextRunner runner = starterRunner(DatabaseAutoConfiguration.class);

    @Test
    @DisplayName("database starter acquires only the configured lazy JDBC controller")
    void databaseStarter() {
      runner
          .withPropertyValues(
              "taf.database.enabled=true",
              "taf.database.connections.orders.jdbc-url=jdbc:taf-test://localhost/orders")
          .run(
              context -> {
                assertThat(context)
                    .hasSingleBean(TestSessionFactory.class)
                    .hasSingleBean(SutConnectionRegistry.class);
                assertUnrelatedCapabilitiesAreAbsent(context, "database");
                try (var session = context.getBean(TestSessionFactory.class).create()) {
                  assertThat(
                          session
                              .getControllerRegistry()
                              .hasController(DatabaseController.class, "orders"))
                      .isTrue();
                }
              });
    }
  }

  @Nested
  @DisplayName("Mobile starter isolation")
  class MobileStarterIsolation {
    private final ApplicationContextRunner runner = starterRunner(AndroidAutoConfiguration.class);

    @Test
    @DisplayName("mobile starter acquires only the configured lazy UiAutomator2 controller")
    void mobileStarter() {
      runner
          .withPropertyValues(
              "taf.mobile.android.enabled=true",
              "taf.mobile.android.controllers.device.server-url=http://127.0.0.1:4723",
              "taf.mobile.android.controllers.device.device-name=emulator",
              "taf.mobile.android.controllers.device.app-package=com.example.app")
          .run(
              context -> {
                assertThat(context)
                    .hasSingleBean(TestSessionFactory.class)
                    .hasSingleBean(AndroidControllerFactory.class);
                assertUnrelatedCapabilitiesAreAbsent(context, "mobile");
                try (var session = context.getBean(TestSessionFactory.class).create()) {
                  assertThat(
                          session
                              .getControllerRegistry()
                              .hasController(AndroidController.class, "device"))
                      .isTrue();
                }
              });
    }
  }

  @Nested
  @DisplayName("Combined starter composition")
  class CombinedStarterComposition {
    private final ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    TafRuntimeAutoConfiguration.class,
                    PlaywrightAutoConfiguration.class,
                    RestAutoConfiguration.class,
                    DatabaseAutoConfiguration.class,
                    AndroidAutoConfiguration.class));

    @Test
    @DisplayName("all four starters share one session factory without bean conflicts")
    void allStartersShareOneSessionFactory() {
      runner
          .withPropertyValues(
              "taf.web.playwright.enabled=true",
              "taf.web.playwright.controllers.browser.base-url=https://example.test",
              "taf.api.rest.enabled=true",
              "taf.api.rest.controllers.catalog.base-url=https://example.test",
              "taf.database.enabled=true",
              "taf.database.connections.orders.jdbc-url=jdbc:taf-test://localhost/orders",
              "taf.mobile.android.enabled=true",
              "taf.mobile.android.controllers.device.server-url=http://127.0.0.1:4723",
              "taf.mobile.android.controllers.device.device-name=emulator",
              "taf.mobile.android.controllers.device.app-package=com.example.app")
          .run(
              context -> {
                assertThat(context)
                    .hasSingleBean(TestSessionFactory.class)
                    .hasSingleBean(PlaywrightControllerFactory.class)
                    .hasSingleBean(RestControllerFactory.class)
                    .hasSingleBean(SutConnectionRegistry.class)
                    .hasSingleBean(AndroidControllerFactory.class);

                try (var session = context.getBean(TestSessionFactory.class).create()) {
                  var controllers = session.getControllerRegistry();
                  assertThat(controllers.hasController(PlaywrightController.class, "browser"))
                      .isTrue();
                  assertThat(controllers.hasController(RestController.class, "catalog")).isTrue();
                  assertThat(controllers.hasController(DatabaseController.class, "orders"))
                      .isTrue();
                  assertThat(controllers.hasController(AndroidController.class, "device")).isTrue();
                }
              });
    }
  }

  private static ApplicationContextRunner starterRunner(Class<?> capabilityAutoConfiguration) {
    return new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(TafRuntimeAutoConfiguration.class, capabilityAutoConfiguration));
  }

  private static void assertUnrelatedCapabilitiesAreAbsent(
      org.springframework.context.ApplicationContext context, String selected) {
    if (!selected.equals("web"))
      assertThat(context.getBeansOfType(PlaywrightControllerFactory.class)).isEmpty();
    if (!selected.equals("api"))
      assertThat(context.getBeansOfType(RestControllerFactory.class)).isEmpty();
    if (!selected.equals("database"))
      assertThat(context.getBeansOfType(SutConnectionRegistry.class)).isEmpty();
    if (!selected.equals("mobile"))
      assertThat(context.getBeansOfType(AndroidControllerFactory.class)).isEmpty();
  }
}

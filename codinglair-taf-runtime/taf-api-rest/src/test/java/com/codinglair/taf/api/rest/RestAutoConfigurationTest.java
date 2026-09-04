package com.codinglair.taf.api.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("REST auto-configuration")
class RestAutoConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  RestAutoConfiguration.class, TafRuntimeAutoConfiguration.class));

  @Nested
  @DisplayName("conditions")
  class Conditions {
    @Test
    @DisplayName("is disabled by default")
    void disabled() {
      runner.run(context -> assertThat(context).doesNotHaveBean(RestControllerFactory.class));
    }

    @Test
    @DisplayName("reports invalid configuration through preflight")
    void invalid() {
      runner
          .withPropertyValues("taf.api.rest.enabled=true")
          .run(
              context ->
                  assertThat(
                          context
                              .getBean(
                                  com.codinglair.taf.runtime.core.preflight.ConsumerPreflight.class)
                              .inspect()
                              .diagnostics())
                      .anyMatch(d -> d.checkId().equals("api-rest.default")));
    }

    @Test
    @DisplayName("registers isolated lazy named controllers")
    void named() {
      runner
          .withPropertyValues(
              "taf.api.rest.enabled=true",
              "taf.api.rest.controllers.catalog.base-url=https://example.test")
          .run(
              context -> {
                var factory = context.getBean(TestSessionFactory.class);
                try (var first = factory.create();
                    var second = factory.create()) {
                  var one = first.getController(RestController.class, "catalog");
                  var two = second.getController(RestController.class, "catalog");
                  assertThat(one).isNotSameAs(two);
                }
              });
    }
  }
}

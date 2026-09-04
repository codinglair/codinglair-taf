package com.codinglair.taf.web.playwright;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.reporting.annotation.ControllerAction;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PlaywrightReportingContractTest {
  @Test
  void meaningfulControllerOperationsUseExactlyOneNeutralAnnotation() {
    Set<String> plumbing =
        Set.of(
            "identity",
            "state",
            "initialize",
            "health",
            "collectArtifacts",
            "close",
            "page",
            "browserContext",
            "browser",
            "locator",
            "byRole",
            "byText",
            "byLabel",
            "byTestId",
            "frame",
            "pages",
            "requests");
    Set<String> expected =
        Arrays.stream(PlaywrightController.class.getMethods())
            .map(Method::getName)
            .filter(name -> !plumbing.contains(name))
            .collect(Collectors.toSet());
    Set<String> annotated =
        Arrays.stream(DefaultPlaywrightController.class.getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(ControllerAction.class))
            .map(Method::getName)
            .collect(Collectors.toSet());

    assertThat(annotated).isEqualTo(expected);
    assertThat(
            Arrays.stream(DefaultPlaywrightController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(ControllerAction.class)))
        .allMatch(method -> method.getAnnotationsByType(ControllerAction.class).length == 1);
  }
}

package com.codinglair.taf.runtime.cucumber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.codinglair.taf.runtime.definition.DefinitionState;
import com.codinglair.taf.runtime.definition.TestDefinition;
import com.codinglair.taf.runtime.definition.TestDefinitionRepository;
import com.codinglair.taf.runtime.definition.TestDefinitionResolver;
import io.cucumber.core.backend.TestCaseState;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CucumberAudienceAndDependencyContractTest {
  @Test
  void approvedTestNgAdapterIsRequiredWithoutCouplingTechnicalAdapterToCucumber() throws Exception {
    assertThat(AbstractTestNGCucumberTests.class).isAssignableFrom(AbstractCucumberRunner.class);

    Path module = Path.of(System.getProperty("basedir"));
    assertThat(Files.readString(module.resolve("pom.xml"))).contains("cucumber-testng");
    assertThat(Files.readString(module.resolve("../codinglair-taf-runner-testng/pom.xml")))
        .doesNotContain("cucumber-java", "cucumber-core", "cucumber-testng");
  }

  @Test
  void dedicatedBusinessPluginDoesNotUseTechnicalReporter() throws Exception {
    String resource =
        "/" + CucumberBusinessReportPlugin.class.getName().replace('.', '/') + ".class";
    try (var bytecode = CucumberBusinessReportPlugin.class.getResourceAsStream(resource)) {
      assertThat(bytecode).isNotNull();
      assertThat(bytecode.readAllBytes())
          .asString()
          .doesNotContain("reporting/abstraction/TestReporter", "taf/runtime/testng");
    }
  }

  @Test
  void cucumberSessionRequiresHookManagedLifecycle() {
    CucumberScenarioSession session = new CucumberScenarioSession();

    assertThatIllegalStateException()
        .isThrownBy(session::session)
        .withMessageContaining("No TestSession");
    session.close();
  }

  @Test
  void onlyTafCucumberHooksPublishesCucumberLifecycleAnnotations() throws Exception {
    assertThat(TafCucumberHooks.class.getMethod("openSession", io.cucumber.java.Scenario.class))
        .matches(method -> method.isAnnotationPresent(Before.class));
    assertThat(TafCucumberHooks.class.getMethod("closeSession"))
        .matches(method -> method.isAnnotationPresent(After.class));
    assertThat(CucumberLifecycleHooks.class.getDeclaredMethods())
        .allMatch(
            method ->
                !method.isAnnotationPresent(Before.class)
                    && !method.isAnnotationPresent(After.class));
  }

  @Test
  void cucumberUsesTheRunnerNeutralDefinitionResolver() throws Exception {
    CucumberScenarioSession session = new CucumberScenarioSession();
    TestCaseState state =
        (TestCaseState)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {TestCaseState.class},
                (proxy, method, arguments) ->
                    switch (method.getName()) {
                      case "getId" -> "scenario-id";
                      case "getName" -> "scenario";
                      case "getStatus" -> io.cucumber.core.backend.Status.PASSED;
                      case "getSourceTagNames" -> Set.of("@test-case-shared-case");
                      case "isFailed" -> false;
                      case "getLine" -> 1;
                      default -> null;
                    });
    var constructor = Scenario.class.getDeclaredConstructor(TestCaseState.class);
    constructor.setAccessible(true);
    Scenario scenario = constructor.newInstance(state);
    session.start(scenario);
    try {
      TestDefinitionResolver resolver = new TestDefinitionResolver(new StaticRepository());
      TestDefinition<String, Integer> definition =
          session.testDefinition(resolver, String.class, Integer.class);
      assertThat(definition.caseId()).isEqualTo("shared-case");
      assertThat(definition.input()).isEqualTo("input");
    } finally {
      session.close();
    }
  }

  private static final class StaticRepository implements TestDefinitionRepository {
    @Override
    public <I, E> TestDefinition<I, E> require(
        String id, Class<I> inputType, Class<E> expectedType) {
      return new TestDefinition<>(
          id,
          1,
          DefinitionState.APPROVED,
          inputType.cast("input"),
          expectedType.cast(7),
          java.util.Map.of());
    }
  }
}

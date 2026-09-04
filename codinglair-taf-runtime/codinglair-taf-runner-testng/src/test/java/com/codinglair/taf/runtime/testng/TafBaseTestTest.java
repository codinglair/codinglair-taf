package com.codinglair.taf.runtime.testng;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.core.annotation.reporting.TafDescription;
import com.codinglair.taf.core.annotation.reporting.TestCaseId;
import com.codinglair.taf.runtime.core.TestSession;
import com.codinglair.taf.runtime.core.controller.ArtifactReason;
import com.codinglair.taf.runtime.core.controller.ControllerContext;
import com.codinglair.taf.runtime.core.controller.ControllerIdentity;
import com.codinglair.taf.runtime.core.controller.ControllerRegistry;
import com.codinglair.taf.runtime.core.controller.ControllerState;
import com.codinglair.taf.runtime.core.controller.HealthResult;
import com.codinglair.taf.runtime.core.controller.TestController;
import com.codinglair.taf.runtime.core.lifecycle.SessionAwareAccessor;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflightContributor;
import com.codinglair.taf.runtime.core.preflight.PreflightDiagnostic;
import com.codinglair.taf.runtime.core.reporting.abstraction.ReportEvent;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestReporter;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestStep;
import com.codinglair.taf.runtime.core.reporting.annotation.PageAction;
import com.codinglair.taf.runtime.definition.CsvTestDefinitionConfiguration;
import com.codinglair.taf.runtime.definition.CsvTestDefinitionRepository;
import com.codinglair.taf.runtime.definition.DefinitionResourceLocation;
import com.codinglair.taf.runtime.definition.DefinitionState;
import com.codinglair.taf.runtime.definition.TestDefinition;
import com.codinglair.taf.runtime.definition.TestDefinitionResolver;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.testng.IConfigurationListener;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.testng.TestNG;
import org.testng.annotations.BeforeMethod;

class TafBaseTestTest {

  @Test
  void runnerPreflightBoundaryDoesNotExposeEnvironmentModule() {
    assertThatThrownBy(
            () ->
                Class.forName(
                    "com.codinglair.taf.runtime.environment.spring.ConsumerPreflight",
                    false,
                    TafBaseTestTest.class.getClassLoader()))
        .isInstanceOf(ClassNotFoundException.class);
  }

  private static volatile String lastFailures = "";

  @BeforeEach
  void resetFixtures() {
    TestConfiguration.created.set(0);
    MetadataConsumer.reset();
    SetupFailureConsumer.cleaned.clear();
    ListenerOnlyConsumer.sawSession = true;
    TestConfiguration.reporter.reset();
    PreflightFailureConfiguration.created.set(0);
    PreflightFailureConfiguration.cleaned.set(0);
    PreflightFailureConsumer.bodyRan = false;
  }

  @Test
  void springManagedLifecycleProvidesInvocationMetadataContextControllersAndServices() {
    TestNG testng = run(MetadataConsumer.class);

    assertThat(testng.hasFailure()).as(failures(testng)).isFalse();
    assertThat(TestConfiguration.created).hasValue(1);
    assertThat(MetadataConsumer.sessionId).isEqualTo("spring-session-1");
    assertThat(MetadataConsumer.contextId).isNotBlank();
    assertThat(MetadataConsumer.testCaseId).isEqualTo("method-case");
    assertThat(MetadataConsumer.description).isEqualTo("resolved description");
    assertThat(MetadataConsumer.controller).isSameAs(TestConfiguration.controller);
    assertThat(MetadataConsumer.page.controller()).isSameAs(TestConfiguration.controller);
    assertThat(MetadataConsumer.marker).isSameAs(TestConfiguration.marker);
    assertThat(MetadataConsumer.definition.caseId()).isEqualTo("method-case");
    assertThat(MetadataConsumer.input).isEqualTo("input");
    assertThat(MetadataConsumer.expected).isEqualTo(7);
    assertThat(TestConfiguration.reporter.events)
        .extracting(ReportEvent::name)
        .contains("accessesFrameworkState", "consumer step", "Open account page");
    assertThat(TestConfiguration.reporter.begun).hasSize(1);
    assertThat(TestConfiguration.reporter.ended).hasSize(1);
    assertThat(MetadataConsumer.cleaned).containsExactly("spring-session-1");
  }

  @Test
  void inheritedAfterMethodCleansSessionWhenConsumerBeforeMethodFails() {
    TestNG testng = run(SetupFailureConsumer.class);

    assertThat(testng.hasFailure()).isTrue();
    assertThat(SetupFailureConsumer.bodyRan).isFalse();
    assertThat(SetupFailureConsumer.cleaned).hasSize(1);
  }

  @Test
  void classTestCaseIdIsUsedWhenMethodHasNoOverride() {
    ClassMetadataConsumer.resolved = null;

    TestNG testng = run(ClassMetadataConsumer.class);

    assertThat(testng.hasFailure()).as(failures(testng)).isFalse();
    assertThat(ClassMetadataConsumer.resolved).isEqualTo("class-only-case");
  }

  @Test
  void missingTestCaseIdProducesActionableFailureWithoutLeakingSession() {
    MissingMetadataConsumer.message = null;

    TestNG testng = run(MissingMetadataConsumer.class);

    assertThat(testng.hasFailure()).as(failures(testng)).isFalse();
    assertThat(MissingMetadataConsumer.message).contains("No @TestCaseId");
  }

  @Test
  void observerListenerDoesNotCreateSessionForClassWithoutTafBaseTest() {
    TestNG testng = run(ListenerOnlyConsumer.class);

    assertThat(testng.hasFailure()).isFalse();
    assertThat(ListenerOnlyConsumer.sawSession).isFalse();
    assertThat(TestConfiguration.created).hasValue(0);
  }

  @Test
  void aggregatedPreflightBlocksTestActivityAndStillCleansTheSingleSession() {
    TestNG testng = run(PreflightFailureConsumer.class);

    assertThat(testng.hasFailure()).isTrue();
    assertThat(PreflightFailureConsumer.bodyRan).isFalse();
    assertThat(PreflightFailureConfiguration.created).hasValue(1);
    assertThat(PreflightFailureConfiguration.cleaned).hasValue(1);
  }

  @Test
  void typedConveniencesResolvePairedCsvDefinitionThroughProviderSpi() {
    CsvDefinitionConsumer.input = null;
    CsvDefinitionConsumer.expected = null;

    TestNG testng = run(CsvDefinitionConsumer.class);

    assertThat(testng.hasFailure()).as(failures(testng)).isFalse();
    assertThat(CsvDefinitionConsumer.input).isEqualTo(new LoginInput("alice", 3));
    assertThat(CsvDefinitionConsumer.expected).isEqualTo(new LoginExpectation("accepted", 3));
  }

  private static TestNG run(Class<?> type) {
    TestNG testng = new TestNG(false);
    List<Throwable> failures = new java.util.concurrent.CopyOnWriteArrayList<>();
    testng.addListener(
        new ITestListener() {
          @Override
          public void onTestFailure(ITestResult result) {
            failures.add(result.getThrowable());
          }
        });
    testng.addListener(
        new IConfigurationListener() {
          @Override
          public void onConfigurationFailure(ITestResult result) {
            failures.add(result.getThrowable());
          }
        });
    testng.setUseDefaultListeners(false);
    testng.setTestClasses(new Class<?>[] {type});
    testng.run();
    lastFailures =
        failures.stream()
            .map(String::valueOf)
            .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
    return testng;
  }

  private static String failures(TestNG testng) {
    return lastFailures;
  }

  @TestCaseId("class-case")
  @ContextConfiguration(classes = TestConfiguration.class)
  public static class MetadataConsumer extends TafBaseTest {
    static String sessionId;
    static String contextId;
    static String testCaseId;
    static String description;
    static SampleController controller;
    static MarkerService marker;
    static SamplePage page;
    static TestDefinition<String, Integer> definition;
    static String input;
    static Integer expected;
    static final Set<String> cleaned = ConcurrentHashMap.newKeySet();

    @Autowired MarkerService injectedMarker;
    @Autowired SamplePageFactory injectedPageFactory;
    @Autowired SpringManagedPage springManagedPage;

    static void reset() {
      sessionId = null;
      contextId = null;
      testCaseId = null;
      description = null;
      controller = null;
      marker = null;
      page = null;
      definition = null;
      input = null;
      expected = null;
      cleaned.clear();
    }

    @org.testng.annotations.Test
    @TestCaseId("method-case")
    @TafDescription("resolved description")
    public void accessesFrameworkState() {
      sessionId = testSession().getSessionId();
      contextId = testContext().getContextId();
      testCaseId = testCaseId();
      description = tafDescription().orElseThrow();
      controller = controller(SampleController.class, "sample");
      marker = injectedMarker;
      definition = testDefinition(String.class, Integer.class);
      input = testInput(String.class);
      expected = expectedOutput(Integer.class);
      step("consumer step", () -> page = injectedPageFactory.create());
      springManagedPage.open();
      testSession().addCleanupListener(cleaned::add);
    }
  }

  @ContextConfiguration(classes = TestConfiguration.class)
  public static class SetupFailureConsumer extends TafBaseTest {
    static final Set<String> cleaned = ConcurrentHashMap.newKeySet();
    static volatile boolean bodyRan;

    @BeforeMethod
    public void consumerSetup() {
      bodyRan = false;
      testSession().addCleanupListener(cleaned::add);
      throw new IllegalStateException("consumer setup failed");
    }

    @org.testng.annotations.Test
    public void neverRuns() {
      bodyRan = true;
    }
  }

  @TestCaseId("class-only-case")
  @ContextConfiguration(classes = TestConfiguration.class)
  public static class ClassMetadataConsumer extends TafBaseTest {
    static String resolved;

    @org.testng.annotations.Test
    public void resolvesClassDefault() {
      resolved = testCaseId();
    }
  }

  @ContextConfiguration(classes = TestConfiguration.class)
  public static class MissingMetadataConsumer extends TafBaseTest {
    static String message;

    @org.testng.annotations.Test
    public void rejectsMissingIdentifier() {
      try {
        testCaseId();
      } catch (IllegalStateException failure) {
        message = failure.getMessage();
      }
    }
  }

  public static class ListenerOnlyConsumer {
    static volatile boolean sawSession;

    @org.testng.annotations.Test
    public void listenerOnly() {
      sawSession = SessionFactory.hasSession(org.testng.Reporter.getCurrentTestResult());
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class TestConfiguration {
    static final AtomicInteger created = new AtomicInteger();
    static final MarkerService marker = new MarkerService();
    static final SampleController controller = new SampleController();
    static final RecordingReporter reporter = new RecordingReporter();

    @Bean
    TestSessionFactory testSessionFactory() {
      return () -> {
        int sequence = created.incrementAndGet();
        ControllerRegistry registry = new ControllerRegistry();
        registry.register(SampleController.class, "sample", controller);
        return TestSession.create("spring-session-" + sequence, registry, List.of());
      };
    }

    @Bean
    MarkerService markerService() {
      return marker;
    }

    @Bean
    TestReporter testReporter() {
      reporter.reset();
      return reporter;
    }

    @Bean
    TestDefinitionResolver testDefinitionResolver() {
      return new TestDefinitionResolver(
          new com.codinglair.taf.runtime.definition.TestDefinitionRepository() {
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
          });
    }

    @Bean
    SamplePageFactory samplePageFactory(SessionAwareAccessor accessor) {
      return new SamplePageFactory(accessor);
    }

    @Bean
    SpringManagedPage springManagedPage() {
      return new SpringManagedPage();
    }
  }

  static final class MarkerService {}

  static final class RecordingReporter implements TestReporter {
    final List<TafTest> begun = new java.util.concurrent.CopyOnWriteArrayList<>();
    final List<TafTest> ended = new java.util.concurrent.CopyOnWriteArrayList<>();
    final List<ReportEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();

    void reset() {
      begun.clear();
      ended.clear();
      events.clear();
    }

    @Override
    public void beginTest(TafTest test) {
      begun.add(test);
    }

    @Override
    public void endTest(TafTest test) {
      ended.add(test);
    }

    @Override
    public void reportStep(TestStep step) {}

    @Override
    public void reportArtifact(TestArtifact artifact) {}

    @Override
    public void reportEvent(ReportEvent event) {
      events.add(event);
    }

    @Override
    public String getName() {
      return "recording";
    }
  }

  @ContextConfiguration(classes = PreflightFailureConfiguration.class)
  public static class PreflightFailureConsumer extends TafBaseTest {
    static volatile boolean bodyRan;

    @org.testng.annotations.Test
    public void blockedBeforeActivity() {
      bodyRan = true;
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class PreflightFailureConfiguration {
    static final AtomicInteger created = new AtomicInteger();
    static final AtomicInteger cleaned = new AtomicInteger();

    @Bean
    TestSessionFactory testSessionFactory() {
      return () -> {
        created.incrementAndGet();
        return TestSession.create(
            "preflight-session",
            new ControllerRegistry(),
            List.of(ignored -> cleaned.incrementAndGet()));
      };
    }

    @Bean
    ConsumerPreflightContributor firstFailure() {
      return () ->
          List.of(
              new PreflightDiagnostic(
                  "configuration.web.url", "URL is unresolved", "Configure the URL"));
    }

    @Bean
    ConsumerPreflightContributor secondFailure() {
      return () ->
          List.of(
              new PreflightDiagnostic(
                  "dependency.browser", "Browser is unavailable", "Install the browser"));
    }
  }

  @TestCaseId("CSV-001")
  @ContextConfiguration(classes = CsvDefinitionConfiguration.class)
  public static class CsvDefinitionConsumer extends TafBaseTest {
    static LoginInput input;
    static LoginExpectation expected;

    @org.testng.annotations.Test
    public void readsCsv() {
      input = testInput(LoginInput.class);
      expected = expectedOutput(LoginExpectation.class);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class CsvDefinitionConfiguration {
    @Bean
    TestDefinitionResolver testDefinitionResolver() {
      return new TestDefinitionResolver(
          new CsvTestDefinitionRepository(
              new CsvTestDefinitionConfiguration(
                  DefinitionResourceLocation.classpath("rt011/inputs.csv"),
                  DefinitionResourceLocation.classpath("rt011/expected.csv"),
                  "caseId")));
    }
  }

  record LoginInput(String username, int attempts) {}

  record LoginExpectation(String status, int count) {}

  record SamplePage(SampleController controller) {}

  static class SpringManagedPage {
    @PageAction("Open account page")
    public void open() {}
  }

  static final class SamplePageFactory {
    private final SessionAwareAccessor accessor;

    SamplePageFactory(SessionAwareAccessor accessor) {
      this.accessor = accessor;
    }

    SamplePage create() {
      return new SamplePage(accessor.controller(SampleController.class, "sample"));
    }
  }

  static final class SampleController implements TestController {
    private volatile ControllerState state = ControllerState.NEW;

    @Override
    public ControllerIdentity identity() {
      return new ControllerIdentity(SampleController.class, "sample");
    }

    @Override
    public ControllerState state() {
      return state;
    }

    @Override
    public void initialize(ControllerContext context) {
      state = ControllerState.READY;
    }

    @Override
    public HealthResult health() {
      return new HealthResult(HealthResult.Status.HEALTHY, "ready", java.util.Map.of());
    }

    @Override
    public Stream<TestArtifact> collectArtifacts(ArtifactReason reason) {
      return Stream.empty();
    }

    @Override
    public void close() {
      state = ControllerState.CLOSED;
    }
  }
}

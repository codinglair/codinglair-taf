package com.codinglair.taf.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.api.rest.RestController;
import com.codinglair.taf.database.DatabaseController;
import com.codinglair.taf.messaging.kafka.KafkaController;
import com.codinglair.taf.runtime.core.TestSession;
import com.codinglair.taf.runtime.core.controller.ControllerContext;
import com.codinglair.taf.runtime.core.controller.ControllerIdentity;
import com.codinglair.taf.runtime.core.controller.ControllerState;
import com.codinglair.taf.runtime.core.controller.HealthResult;
import com.codinglair.taf.runtime.core.controller.TestController;
import com.codinglair.taf.web.playwright.PlaywrightController;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@DisplayName("GATE-005 deterministic capability integration")
class DeterministicCapabilityIntegrationGateTest {

  @Nested
  @DisplayName("Published artifact hygiene")
  @EnabledIfSystemProperty(named = "taf.runtime.gate", matches = "true")
  class PublishedArtifactHygiene {

    @Test
    @DisplayName("Runtime capability jars contain no internal test output")
    void runtimeJarsExcludeInternalTests() throws Exception {
      Path runtime = Path.of("..").toAbsolutePath().normalize();
      List<Path> inspected = new ArrayList<>();

      try (var modules = Files.list(runtime)) {
        for (Path module : modules.filter(Files::isDirectory).toList()) {
          Path testClasses = module.resolve("target/test-classes");
          Path target = module.resolve("target");
          if (!Files.isDirectory(testClasses) || !Files.isDirectory(target)) continue;

          Path jar;
          try (var jars = Files.list(target)) {
            jar =
                jars.filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .filter(path -> !path.getFileName().toString().endsWith("-tests.jar"))
                    .filter(path -> !path.getFileName().toString().endsWith("-sources.jar"))
                    .findFirst()
                    .orElse(null);
          }
          if (jar == null) continue;

          Set<String> internalTestEntries = new HashSet<>();
          try (var files = Files.walk(testClasses)) {
            files
                .filter(Files::isRegularFile)
                .map(testClasses::relativize)
                .map(path -> path.toString().replace('\\', '/'))
                .forEach(internalTestEntries::add);
          }

          try (ZipFile archive = new ZipFile(jar.toFile())) {
            Set<String> published =
                archive.stream()
                    .map(entry -> entry.getName())
                    .collect(java.util.stream.Collectors.toSet());
            assertThat(published.stream().filter(internalTestEntries::contains).toList()).isEmpty();
            assertThat(published).noneMatch(entry -> entry.startsWith("src/test/"));
          }
          inspected.add(jar);
        }
      }

      assertThat(inspected)
          .as("packaged Runtime capability artifacts")
          .hasSizeGreaterThanOrEqualTo(24);
    }
  }

  @Nested
  @DisplayName("Multi-channel session composition")
  class MultiChannelComposition {

    @Test
    @DisplayName("Composes web REST Kafka and database lazily in one isolated session")
    void composesFourCapabilitiesInOneSession() {
      List<String> lifecycle = new ArrayList<>();

      try (TestSession session = TestSession.create()) {
        RecordingController<PlaywrightController> web =
            register(session, PlaywrightController.class, "web", lifecycle);
        RecordingController<RestController> rest =
            register(session, RestController.class, "rest", lifecycle);
        RecordingController<KafkaController> kafka =
            register(session, KafkaController.class, "kafka", lifecycle);
        RecordingController<DatabaseController> database =
            register(session, DatabaseController.class, "database", lifecycle);

        assertThat(List.of(web.state(), rest.state(), kafka.state(), database.state()))
            .containsOnly(ControllerState.NEW);

        assertThat(session.getController(PlaywrightController.class, "web")).isSameAs(web.proxy());
        assertThat(session.getController(RestController.class, "rest")).isSameAs(rest.proxy());
        assertThat(session.getController(KafkaController.class, "kafka")).isSameAs(kafka.proxy());
        assertThat(session.getController(DatabaseController.class, "database"))
            .isSameAs(database.proxy());

        assertThat(List.of(web.state(), rest.state(), kafka.state(), database.state()))
            .containsOnly(ControllerState.READY);
        assertThat(lifecycle)
            .containsExactly(
                "initialize:web", "initialize:rest", "initialize:kafka", "initialize:database");
      }

      assertThat(lifecycle).endsWith("close:database", "close:kafka", "close:rest", "close:web");
    }

    @Test
    @DisplayName("Keeps identically named capabilities isolated across parallel sessions")
    void keepsParallelSessionsIsolated() throws Exception {
      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        List<Callable<String>> tasks =
            Stream.generate(() -> (Callable<String>) this::runIsolatedSession).limit(32).toList();

        List<String> sessionIds = new ArrayList<>();
        for (var result : executor.invokeAll(tasks)) sessionIds.add(result.get());

        assertThat(sessionIds).doesNotHaveDuplicates().hasSize(32);
      }
    }

    private String runIsolatedSession() {
      List<String> lifecycle = new ArrayList<>();
      TestSession session = TestSession.create();
      RecordingController<RestController> rest =
          register(session, RestController.class, "shared-name", lifecycle);
      session.getController(RestController.class, "shared-name");
      String sessionId = rest.initializedSessionId();
      session.close();
      session.close();
      assertThat(lifecycle).containsExactly("initialize:shared-name", "close:shared-name");
      return sessionId;
    }
  }

  private static <T extends TestController> RecordingController<T> register(
      TestSession session, Class<T> type, String name, List<String> lifecycle) {
    RecordingController<T> recording = new RecordingController<>(type, name, lifecycle);
    session.getControllerRegistry().register(type, name, recording.proxy());
    return recording;
  }

  private static final class RecordingController<T extends TestController>
      implements InvocationHandler {
    private final Class<T> type;
    private final String name;
    private final List<String> lifecycle;
    private final AtomicReference<ControllerState> state =
        new AtomicReference<>(ControllerState.NEW);
    private final AtomicReference<String> initializedSessionId = new AtomicReference<>();
    private final T proxy;

    private RecordingController(Class<T> type, String name, List<String> lifecycle) {
      this.type = type;
      this.name = name;
      this.lifecycle = lifecycle;
      this.proxy =
          type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, this));
    }

    private T proxy() {
      return proxy;
    }

    private ControllerState state() {
      return state.get();
    }

    private String initializedSessionId() {
      return initializedSessionId.get();
    }

    @Override
    public Object invoke(Object ignored, Method method, Object[] arguments) {
      return switch (method.getName()) {
        case "identity" -> new ControllerIdentity(type, name);
        case "state" -> state.get();
        case "initialize" -> initialize((ControllerContext) arguments[0]);
        case "health" -> HealthResult.unknown("gate controller");
        case "collectArtifacts" -> Stream.empty();
        case "close" -> close();
        case "toString" -> type.getSimpleName() + "[" + name + "]";
        default -> defaultValue(method.getReturnType());
      };
    }

    private Object initialize(ControllerContext context) {
      initializedSessionId.set(context.sessionId());
      state.set(ControllerState.READY);
      lifecycle.add("initialize:" + name);
      return null;
    }

    private Object close() {
      if (state.getAndSet(ControllerState.CLOSED) != ControllerState.CLOSED)
        lifecycle.add("close:" + name);
      return null;
    }

    private static Object defaultValue(Class<?> type) {
      if (!type.isPrimitive()) return null;
      if (type == boolean.class) return false;
      if (type == char.class) return '\0';
      return 0;
    }
  }
}

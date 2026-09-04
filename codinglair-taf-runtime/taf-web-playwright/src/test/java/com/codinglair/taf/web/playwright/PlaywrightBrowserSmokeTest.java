package com.codinglair.taf.web.playwright;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.TestSession;
import com.codinglair.taf.runtime.core.controller.ArtifactReason;
import com.codinglair.taf.runtime.core.reporting.CurrentReportingContext;
import com.codinglair.taf.runtime.core.reporting.HierarchicalReport;
import com.codinglair.taf.runtime.core.reporting.RedactionPipeline;
import com.codinglair.taf.runtime.core.reporting.ReportingActionInterceptor;
import com.codinglair.taf.runtime.core.reporting.ReportingContext;
import com.codinglair.taf.runtime.core.reporting.abstraction.ReportEvent;
import com.codinglair.taf.runtime.core.reporting.abstraction.ReportLevel;
import com.microsoft.playwright.Locator;
import java.nio.file.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

@Tag("browser-smoke")
@EnabledIfSystemProperty(named = "taf.browser.smoke", matches = "true")
class PlaywrightBrowserSmokeTest {
  @TempDir Path tempDirectory;

  @Test
  void commonInteractionAndWebFirstAssertionMatrix() {
    try (TestSession session = TestSession.create()) {
      PlaywrightController controller =
          controller(session, "matrix", PlaywrightProperties.Engine.CHROMIUM);
      controller.navigate(
          "data:text/html,<label>Name<input id='name'></label><button>Save</button><select id='s'><option>A</option></select><input id='c' type='checkbox'><div id='out'>ready</div>");
      Locator input = controller.byLabel("Name");
      controller.fill(input, "Ada");
      controller.press(input, "End");
      controller.click(controller.byRole(com.microsoft.playwright.options.AriaRole.BUTTON, "Save"));
      controller.selectOption(controller.locator("#s"), "A");
      controller.check(controller.locator("#c"));
      controller.expectVisible(controller.locator("#out"));
      controller.expectText(controller.locator("#out"), "ready");
      assertThat(input.inputValue()).isEqualTo("Ada");
    }
  }

  @Test
  void supportedEngineMatrixLaunchesWithIsolatedContext() {
    for (PlaywrightProperties.Engine engine : PlaywrightProperties.Engine.values()) {
      try (TestSession session = TestSession.create()) {
        PlaywrightController controller = controller(session, engine.name().toLowerCase(), engine);
        controller.navigate("data:text/html,<main>" + engine + "</main>");
        controller.expectText(controller.locator("main"), engine.name());
        assertThat(controller.browserContext().pages()).hasSize(1);
      }
    }
  }

  @Test
  void framesUploadsDialogsNetworkAndAuthenticationStateWork() throws Exception {
    Path upload = Files.writeString(tempDirectory.resolve("upload.txt"), "payload");
    Path state = tempDirectory.resolve("state.json");
    try (TestSession session = TestSession.create()) {
      PlaywrightController controller =
          controller(session, "advanced", PlaywrightProperties.Engine.CHROMIUM);
      controller.route(
          "**/mocked",
          route ->
              route.fulfill(
                  new com.microsoft.playwright.Route.FulfillOptions()
                      .setStatus(200)
                      .setBody("mocked")));
      controller
          .page()
          .setContent(
              "<iframe srcdoc='<p>inside</p>'></iframe><input id='file' type='file'><script>fetch('https://example.test/mocked')</script>");
      controller.expectText(controller.frame("iframe").locator("p"), "inside");
      controller.upload(controller.locator("#file"), upload);
      assertThat(controller.locator("#file").inputValue()).endsWith("upload.txt");
      controller.onDialog(com.microsoft.playwright.Dialog::accept);
      controller.page().evaluate("alert('accepted')");
      controller.saveStorageState(state);
      assertThat(state).exists();
      assertThat(controller.requests()).anyMatch(request -> request.url().endsWith("/mocked"));
    }
  }

  @Test
  void parallelBrowserContextsRemainIsolated() throws Exception {
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Callable<String> first = () -> isolatedValue("first", "alpha");
      Callable<String> second = () -> isolatedValue("second", "beta");
      var values =
          executor.invokeAll(java.util.List.of(first, second)).stream()
              .map(
                  future -> {
                    try {
                      return future.get();
                    } catch (Exception e) {
                      throw new CompletionException(e);
                    }
                  })
              .toList();
      assertThat(values).containsExactlyInAnyOrder("alpha", "beta");
    }
  }

  @Test
  void failureEvidenceIsSanitizedAndAttached() {
    PlaywrightProperties properties = properties(PlaywrightProperties.Engine.CHROMIUM);
    properties.getEvidence().setScreenshotOnFailure(false);
    try (TestSession session = TestSession.create()) {
      PlaywrightController controller = register(session, "evidence", properties);
      controller.navigate("data:text/html,<body>password=super-secret</body>");
      var artifacts = controller.collectArtifacts(ArtifactReason.FAILURE).toList();
      assertThat(artifacts).extracting(a -> a.name()).contains("page.html");
      assertThat(artifacts).allSatisfy(a -> assertThat(a.content()).doesNotContain("super-secret"));
      assertThat(session.getArtifactCollector().getArtifacts()).containsAll(artifacts);
    }
  }

  @Test
  void sessionOwnedControllerActionIsAutomaticallyIntercepted() {
    var current = new CurrentReportingContext();
    var hierarchy = new HierarchicalReport("playwright-reporting", new RedactionPipeline());
    var reporting = new ReportingContext(hierarchy, java.util.List.of());
    current.bind(reporting);
    try (TestSession session = TestSession.create()) {
      session.getControllerRegistry().enableReporting(new ReportingActionInterceptor(current));
      PlaywrightController controller =
          controller(session, "reported", PlaywrightProperties.Engine.CHROMIUM);
      controller.navigate("data:text/html,<main>reported</main>");
      assertThat(
              reporting.events().stream()
                  .filter(event -> event.phase() == ReportEvent.Phase.FINISHED)
                  .filter(event -> event.level() == ReportLevel.CONTROLLER_OPERATION)
                  .map(ReportEvent::name))
          .containsExactly("Navigate browser");
    } finally {
      current.unbind();
    }
  }

  private String isolatedValue(String name, String value) {
    try (TestSession session = TestSession.create()) {
      PlaywrightController controller =
          controller(session, name, PlaywrightProperties.Engine.CHROMIUM);
      controller.navigate("data:text/html,<input id='value'>");
      controller.fill(controller.locator("#value"), value);
      return controller.locator("#value").inputValue();
    }
  }

  private PlaywrightController controller(
      TestSession session, String name, PlaywrightProperties.Engine engine) {
    return register(session, name, properties(engine));
  }

  private PlaywrightController register(
      TestSession session, String name, PlaywrightProperties properties) {
    PlaywrightController value = new DefaultPlaywrightController(name, properties);
    session.getControllerRegistry().register(PlaywrightController.class, name, value);
    return session.getController(PlaywrightController.class, name);
  }

  private PlaywrightProperties properties(PlaywrightProperties.Engine engine) {
    PlaywrightProperties value = new PlaywrightProperties();
    value.setEngine(engine);
    return value;
  }
}

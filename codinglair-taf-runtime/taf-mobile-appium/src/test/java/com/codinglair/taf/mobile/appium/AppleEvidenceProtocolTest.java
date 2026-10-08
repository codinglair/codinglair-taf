package com.codinglair.taf.mobile.appium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codinglair.taf.mobile.appium.exception.AppleControllerException;
import com.codinglair.taf.mobile.appium.service.AppleResourceReservations;
import com.codinglair.taf.mobile.appium.service.AppleTransportSecurity;
import com.codinglair.taf.mobile.appium.service.DefaultAppleController;
import com.codinglair.taf.runtime.core.controller.ArtifactReason;
import com.codinglair.taf.runtime.core.controller.ControllerContext;
import com.codinglair.taf.runtime.core.controller.EnvironmentAccess;
import com.codinglair.taf.runtime.core.reporting.ArtifactCollector;
import com.codinglair.taf.runtime.core.reporting.RedactionPipeline;
import com.codinglair.taf.runtime.core.reporting.ReporterDispatcher;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestReporter;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestStep;
import com.codinglair.taf.runtime.core.security.ArtifactDownloadTransport;
import com.codinglair.taf.runtime.core.security.ResourceAccess;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Apple conditional evidence through the real Appium client protocol")
class AppleEvidenceProtocolTest extends AppleProtocolFixture {
  private ControllerContext evidenceContext;

  private void open(boolean visual, boolean video, boolean strict) {
    var settings = settings();
    settings.setAllowVisualArtifacts(visual);
    settings.setVideo(video);
    settings.setRequireEvidence(strict);
    evidenceContext = TestContexts.context();
    controller = new DefaultAppleController("evidence", settings, null);
    controller.initialize(evidenceContext);
  }

  @ParameterizedTest
  @ValueSource(strings = {"available", "unsupported", "unavailable", "collection-failed"})
  @DisplayName("Each requested artifact reports a deterministic outcome without changing readiness")
  void outcomes(String outcome) {
    evidenceOutcome = outcome;
    open(true, true, false);
    var artifacts = controller.collectArtifacts(ArtifactReason.FAILURE).toList();
    for (String type : List.of("screenshot", "page-source", "device-log", "video")) {
      String expected = outcome;
      assertThat(artifacts)
          .anyMatch(
              artifact -> artifact.content().equals("artifact=" + type + "; outcome=" + expected));
    }
    assertThat(controller.health().status().name()).isEqualTo("HEALTHY");
    assertThat(evidenceContext.artifacts().getArtifacts()).hasSize(artifacts.size());
    var reported = new ArrayList<TestArtifact>();
    TestReporter reporter =
        new TestReporter() {
          public void beginTest(TafTest test) {}

          public void endTest(TafTest test) {}

          public void reportStep(TestStep step) {}

          public void reportArtifact(TestArtifact artifact) {
            reported.add(artifact);
          }

          public String getName() {
            return "fixture";
          }
        };
    var dispatcher = new ReporterDispatcher(reporter, new RedactionPipeline());
    evidenceContext.artifacts().finalizeEvidence(dispatcher);
    evidenceContext.artifacts().finalizeEvidence(dispatcher);
    assertThat(reported).containsExactlyElementsOf(evidenceContext.artifacts().getArtifacts());
    assertThat(reported).allMatch(artifact -> !artifact.content().contains("CANARY"));
  }

  @Test
  @DisplayName("Default security policy suppresses source and screenshot before capture")
  void suppressed() {
    open(false, false, false);
    var artifacts = controller.collectArtifacts(ArtifactReason.FAILURE).toList();
    assertThat(artifacts)
        .anyMatch(
            artifact -> artifact.content().equals("artifact=screenshot; outcome=unavailable"));
    assertThat(artifacts)
        .anyMatch(
            artifact -> artifact.content().equals("artifact=page-source; outcome=unavailable"));
    assertThat(requests)
        .noneMatch(request -> request.contains("/screenshot") || request.contains("/source"));
    assertThat(requests).anyMatch(request -> request.contains("type=syslog"));
    assertThat(requests)
        .noneMatch(request -> request.contains("type=server") || request.contains("logcat"));
  }

  @Test
  @DisplayName("Strict policy records unavailable evidence and still quits exactly once")
  void strictCleanup() {
    evidenceOutcome = "unsupported";
    open(true, true, true);
    assertThrows(
        AppleControllerException.class, () -> controller.collectArtifacts(ArtifactReason.FAILURE));
    assertThat(evidenceContext.artifacts().getArtifacts())
        .anyMatch(artifact -> artifact.content().contains("outcome=unsupported"));
    assertThrows(AppleControllerException.class, controller::close);
    controller.close();
    assertThat(requests.stream().filter(request -> request.startsWith("DELETE /session/fixture")))
        .hasSize(1);
  }

  @Test
  @DisplayName("Recording stops and evidence finishes before teardown with idempotent close")
  void ordering() {
    open(true, true, false);
    controller.close();
    int stop = index("/stop_recording_screen");
    int quit = index("DELETE /session/fixture");
    assertThat(stop).isGreaterThan(index("/start_recording_screen")).isLessThan(quit);
    assertThat(index("/log")).isLessThan(quit);
    int count = requests.size();
    controller.close();
    assertThat(requests).hasSize(count);
  }

  @Test
  @DisplayName("Strict policy accepts previously retained video without stopping twice")
  void strictAvailable() {
    open(true, true, true);
    sourceValue = "<page>Business outcome=failed</page>";
    controller.collectArtifacts(ArtifactReason.FAILURE).close();
    controller.close();
    assertThat(requests.stream().filter(request -> request.contains("/stop_recording_screen")))
        .hasSize(1);
    assertThat(
            evidenceContext.artifacts().getArtifacts().stream()
                .filter(artifact -> artifact.type().equals("video")))
        .hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-base64", "https://127.0.0.1:1/video?token=CANARY"})
  @DisplayName("Malformed binary and unauthorized provider links cannot leak or bypass trust")
  void invalidBinary(String value) {
    open(true, true, false);
    evidenceValue = value;
    var artifacts = controller.collectArtifacts(ArtifactReason.FAILURE).toList();
    assertThat(artifacts)
        .anyMatch(
            artifact -> artifact.content().equals("artifact=video; outcome=collection-failed"));
    assertThat(artifacts)
        .noneMatch(
            artifact ->
                artifact.content().contains("token=") || artifact.content().contains("CANARY"));
    controller.close();
    assertThat(index("DELETE /session/fixture")).isGreaterThan(0);
  }

  @Test
  @DisplayName("Capture budget and content size bound storage without blocking cleanup")
  void bounded() {
    open(true, true, false);
    evidenceValue = "x".repeat(3 * 1024 * 1024);
    var first = controller.collectArtifacts(ArtifactReason.FAILURE).toList();
    assertThat(first)
        .anyMatch(
            artifact ->
                artifact.content().equals("artifact=page-source; outcome=collection-failed"));
    for (int i = 0; i < 10; i++) controller.collectArtifacts(ArtifactReason.FAILURE).close();
    assertThat(evidenceContext.artifacts().getArtifacts().size()).isLessThanOrEqualTo(36);
    controller.close();
    assertThat(index("DELETE /session/fixture")).isGreaterThan(0);
  }

  @Test
  @DisplayName("Separate controller contexts retain distinct artifact names and collectors")
  void isolation() {
    open(true, false, false);
    var first = controller.collectArtifacts(ArtifactReason.FAILURE).toList();
    var otherCollector = new ArtifactCollector(TafTest.of("other", "fixture"), "other", "other");
    var other = new DefaultAppleController("other", settings(), null);
    try {
      other.initialize(
          new ControllerContext("other", EnvironmentAccess.unavailable(), otherCollector));
      var second = other.collectArtifacts(ArtifactReason.FAILURE).toList();
      assertThat(first.stream().map(TestArtifact::name).toList())
          .doesNotContainAnyElementsOf(second.stream().map(TestArtifact::name).toList());
      assertThat(otherCollector.getArtifacts()).hasSize(second.size());
      assertThat(evidenceContext.artifacts().getArtifacts()).hasSize(first.size());
    } finally {
      other.close();
    }
  }

  private int index(String text) {
    for (int i = 0; i < requests.size(); i++) if (requests.get(i).contains(text)) return i;
    return -1;
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  @DisplayName("Signed provider video is fetched only with an exact artifact destination grant")
  void providerVideo(boolean authorized) {
    var downloads = new AtomicInteger();
    server.createContext(
        "/video.mp4",
        exchange -> {
          downloads.incrementAndGet();
          assertThat(exchange.getRequestURI().getRawQuery()).isEqualTo("token=CANARY");
          assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isNull();
          byte[] bytes = "video".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, bytes.length);
          try (var output = exchange.getResponseBody()) {
            output.write(bytes);
          }
        });
    var settings = settings();
    settings.setVideo(true);
    settings.setAllowVisualArtifacts(true);
    URI uri = URI.create(settings.getServerUrl() + "/video.mp4?token=CANARY");
    String destination = ArtifactDownloadTransport.destination(uri);
    var security =
        new AppleTransportSecurity(
            access ->
                access.kind() != ResourceAccess.Kind.ARTIFACT_DESTINATION
                    || authorized && access.resource().equals(destination),
            null,
            "fixture");
    evidenceContext = TestContexts.context();
    controller =
        new DefaultAppleController(
            "provider", settings, null, new AppleResourceReservations(), security);
    controller.initialize(evidenceContext);
    evidenceValue = uri.toString();
    var artifacts = controller.collectArtifacts(ArtifactReason.DIAGNOSTIC).toList();
    assertThat(downloads.get()).isEqualTo(authorized ? 1 : 0);
    assertThat(artifacts)
        .anyMatch(
            artifact ->
                artifact
                    .content()
                    .equals(
                        "artifact=video; outcome="
                            + (authorized ? "available" : "collection-failed")));
    assertThat(artifacts)
        .noneMatch(
            artifact ->
                artifact.content().contains("CANARY") || artifact.content().contains("token="));
  }

  @Test
  @DisplayName("Concurrent capture and close serialize and never contact a deleted session")
  void captureCloseRace() throws Exception {
    open(true, true, false);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var capture =
          executor.submit(() -> controller.collectArtifacts(ArtifactReason.FAILURE).toList());
      var close = executor.submit(controller::close);
      capture.get(5, TimeUnit.SECONDS);
      close.get(5, TimeUnit.SECONDS);
    }
    assertThat(index("DELETE /session/fixture")).isEqualTo(requests.size() - 1);
    assertThat(requests.stream().filter(request -> request.contains("/stop_recording_screen")))
        .hasSize(1);
  }
}

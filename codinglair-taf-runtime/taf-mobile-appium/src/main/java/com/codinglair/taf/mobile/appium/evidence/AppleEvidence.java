package com.codinglair.taf.mobile.appium.evidence;

import com.codinglair.taf.mobile.appium.configuration.AppleControllerSettings;
import com.codinglair.taf.runtime.core.controller.ArtifactReason;
import com.codinglair.taf.runtime.core.controller.ControllerContext;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.IOSStartScreenRecordingOptions;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.UnsupportedCommandException;

/** Controller-owned, bounded evidence; accessed under the controller lifecycle lock. */
public final class AppleEvidence {
  private static final int MAX_BYTES = 2 * 1024 * 1024;
  private final AppleControllerSettings settings;
  private final ControllerContext context;
  private final String prefix = "apple-" + UUID.randomUUID();
  private int captures;
  private boolean recording;
  private String videoOutcome = "unavailable";

  public AppleEvidence(AppleControllerSettings settings, ControllerContext context) {
    this.settings = settings;
    this.context = context;
  }

  public void start(IOSDriver driver) {
    if (!settings.getVideo()) return;
    try {
      driver.startRecordingScreen(
          new IOSStartScreenRecordingOptions().withTimeLimit(Duration.ofMinutes(2)));
      recording = true;
    } catch (UnsupportedCommandException | UnsupportedOperationException _) {
      videoOutcome = "unsupported";
    } catch (RuntimeException _) {
      videoOutcome = "collection-failed";
    }
  }

  /** Returns redacted artifacts; the controller publishes once through its session collector. */
  public List<TestArtifact> collect(IOSDriver driver, ArtifactReason reason) {
    if (captures >= 4) return captureBudgetExhausted(driver);
    String batch = prefix + "-" + ++captures;
    var artifacts = new ArrayList<TestArtifact>();
    captureMetadata(driver, artifacts, batch);
    captureFailureEvidence(driver, reason, artifacts, batch);
    captureDeviceLog(driver, artifacts, batch);
    captureVideo(driver, artifacts, batch);
    return List.copyOf(artifacts);
  }

  private List<TestArtifact> captureBudgetExhausted(IOSDriver driver) {
    // At most four capture batches, including teardown; no unbounded session accumulation.
    stop(driver);
    if (captures++ != 4) return List.of();
    var artifacts = new ArrayList<TestArtifact>();
    outcome(artifacts, prefix, "capture-budget", "unavailable");
    if (settings.getVideo()) outcome(artifacts, prefix, "video", videoOutcome);
    return List.copyOf(artifacts);
  }

  private void captureMetadata(IOSDriver driver, List<TestArtifact> artifacts, String batch) {
    capture(
        artifacts,
        batch,
        "metadata",
        "text/plain",
        () -> {
          String nativeContext = driver.getContext();
          if (nativeContext == null) return "";
          // Retain context category only; WebView identifiers and raw capabilities are sensitive.
          String category = "NATIVE_APP".equals(nativeContext) ? "native" : "web";
          Object version = driver.getCapabilities().getCapability("platformVersion");
          String safeVersion =
              version instanceof String text && text.matches("[0-9.]{1,32}") ? text : "unknown";
          return "family="
              + settings.family()
              + "; context="
              + category
              + "; version="
              + safeVersion;
        });
  }

  private void captureFailureEvidence(
      IOSDriver driver, ArtifactReason reason, List<TestArtifact> artifacts, String batch) {
    boolean failure = reason == ArtifactReason.FAILURE || reason == ArtifactReason.CLEANUP_FAILURE;
    if ((failure || reason == ArtifactReason.EXPLICIT) && settings.getScreenshotOnFailure()) {
      if (!settings.getAllowVisualArtifacts())
        outcome(artifacts, batch, "screenshot", "unavailable");
      else
        capture(
            artifacts,
            batch,
            "screenshot",
            "image/png",
            () -> encoded(driver.getScreenshotAs(OutputType.BASE64)));
    }
    if ((failure || reason == ArtifactReason.EXPLICIT) && settings.getPageSourceOnFailure()) {
      if (!settings.getAllowVisualArtifacts())
        outcome(artifacts, batch, "page-source", "unavailable");
      else capture(artifacts, batch, "page-source", "application/xml", driver::getPageSource);
    }
  }

  private void captureDeviceLog(IOSDriver driver, List<TestArtifact> artifacts, String batch) {
    if (settings.getDeviceLogs())
      capture(
          artifacts,
          batch,
          "device-log",
          "text/plain",
          () -> {
            // XCUITest syslog is genuine device/system output. Never request server logs or Logcat.
            if (!driver.manage().logs().getAvailableLogTypes().contains("syslog"))
              throw new UnsupportedOperationException();
            var lines = new StringBuilder();
            for (var entry : driver.manage().logs().get("syslog")) {
              String line = entry.toString();
              if (lines.length() + line.length() + 1 > MAX_BYTES) throw new IllegalStateException();
              lines.append(line).append('\n');
            }
            return lines.toString();
          });
  }

  private void captureVideo(IOSDriver driver, List<TestArtifact> artifacts, String batch) {
    if (settings.getVideo()) {
      if (recording)
        capture(
            artifacts,
            batch,
            "video",
            "video/mp4",
            () -> {
              recording = false;
              return encoded(driver.stopRecordingScreen());
            });
      else outcome(artifacts, batch, "video", videoOutcome);
    }
  }

  private String encoded(String value) {
    if (value == null || value.isBlank()) return "";
    if (value.length() > MAX_BYTES * 4 / 3 + 4) throw new IllegalStateException();
    byte[] decoded = Base64.getDecoder().decode(value);
    if (decoded.length > MAX_BYTES) throw new IllegalStateException();
    return Base64.getEncoder().encodeToString(decoded);
  }

  private void capture(
      List<TestArtifact> artifacts,
      String batch,
      String type,
      String contentType,
      Supplier<String> supplier) {
    try {
      String value = supplier.get();
      if (value == null || value.isBlank()) {
        outcome(artifacts, batch, type, "unavailable");
        return;
      }
      int maximum =
          type.equals("video") || type.equals("screenshot") ? MAX_BYTES * 4 / 3 + 4 : MAX_BYTES;
      if (value.getBytes(StandardCharsets.UTF_8).length > maximum)
        throw new IllegalStateException();
      String safe = context.artifacts().redact(value);
      artifacts.add(TestArtifact.of(batch + "-" + type, type, safe, contentType));
      outcome(artifacts, batch, type, "available");
    } catch (UnsupportedCommandException | UnsupportedOperationException _) {
      outcome(artifacts, batch, type, "unsupported");
    } catch (RuntimeException _) {
      outcome(artifacts, batch, type, "collection-failed");
    }
  }

  private void outcome(List<TestArtifact> artifacts, String batch, String type, String status) {
    if (type.equals("video")) videoOutcome = status;
    artifacts.add(
        TestArtifact.of(
            batch + "-" + type + "-availability",
            "diagnostic",
            "artifact=" + type + "; outcome=" + status,
            "text/plain"));
  }

  /** Best-effort stop after exhausted capture budget; cleanup must still proceed. */
  private void stop(IOSDriver driver) {
    if (!recording) return;
    recording = false;
    try {
      driver.stopRecordingScreen();
    } catch (RuntimeException _) {
      videoOutcome = "collection-failed";
    }
  }
}
